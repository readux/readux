(ns readux.core
  (:require [cljs.spec :as s]
            [reagent.core :as r]
            [readux.store :as rds]
            [readux.utils :as rdu]
            [readux.spec :as spec])
  (:require-macros [reagent.ratom :refer [reaction]]))

;; Helpers
(defn- query-reg!*
  [existing-fn new-fn query-id]
  (when-not (nil? existing-fn)
    (->> (str "Overriding existing query '" query-id "'!")
         (.warn js/console)))
  new-fn)

(defn- ns-abs
  "Prefixes keyword kw with namespace ns if no ns found.

  Note: ns/kw can be strings or keywords

  Arguments:
  ns  - namespace, e.g. :foo / 'foo'
  kw  - keyword, e.g. :bar / 'bar'

  Examples:
  (ns-abs :baz :foo) => :baz/foo
  (ns-abs :baz :bar/foo => :bar/foo"
  [ns kw]
  {:pre [(spec/kw-or-str? ns)
         (spec/kw-or-str? kw)]
   :post [#(spec/kw? %1)]}
  (if (-> kw namespace nil?)
    (keyword (name ns) (name kw))
    kw))

;; ----
(defn dispatch
  ([store action]
   {:pre
    [(spec/action? action)]}
   ((@store :dispatch) store action))
  ([store type payload]
   {:pre
    [(rdu/spec-valid?
       :readux.action/type type "Expect action type to be a keyword value")
     (rdu/spec-valid? :readux.action/payload payload)]}
   #_(assert (keyword? type) "Expect action type to be a keyword value")
   (dispatch store {:type type :payload payload})))

(defn store
  ([reducer] (store reducer identity))
  ([reducer enhancer]
   (let [store (enhancer (rds/->store* reducer))]
     (dispatch store {:type :readux/init})
     store)))

(defn reducer
  "Construct reducer from one (or more) action maps.

  Action maps are maps whose key is the action type and whose value is a
  function taking two arguments, 'model' and 'action', outputting the model
  which results from processing the action.
  I.e.
  ---
  {:incr (fn [model action] (update model :value inc))
   :decr (fn [model action] (update model :value dec))}
  ---

  NOTE: If several action maps are supplied, they are merged in-order."
  [init action-map & action-maps]
  {:pre [(s/valid? :readux/action-map action-map)
         (-> (s/nilable (s/coll-of :readux/action-map))
             (s/valid? action-maps))]}
  (let [action-map (reduce merge action-map action-maps)]
    (fn [model action]
      (let [model (or model init)]
        (if-let [handler (->> action :type (get action-map))]
          (handler model action)
          model)))))

(defn- fn-map->reducer*
  "Creates a reducer from a map of key -> reducer entries.

  Arguments:
  ----------
  reducer-map  - a map of reducers. Each key corresponds to the subtree that
                 the associated reducer function should operate on.

  Example:
  --------
  Given a map of reducers like so:
  {:foo (fn [model action] ...)
   :bar (fn [model action] ...)}

  .. And a model like so:
  {:foo 1
   :bar 100}

  The :foo reducer would get a model of '1' and the :bar reducer
  a model of '100'.

  Given that each reducer returns a result model, the final model is then
  re-assembled from the results of the reducers."
  [action-map]
  {:pre [(spec/action-map? action-map)]}
  (let [action-map (into (sorted-map) action-map)]
    (fn [model action]
      (->> (for [[path reducer] action-map]
             [path (reducer (get model path) action)])
           ;; don't convert into sorted-map - we don't want people relying on
           ;; the structure of the output map. Only dispatch order is guaranteed
           (into {})))))

(defn with-ctx
  "Modify (relative) actions in action map to use supplied context ns."
  [ctx-ns action-map & action-maps]
  {:pre [(spec/kw? ctx-ns)
         (spec/action-map? action-map)
         (spec/nil-or-action-maps? action-maps)]}
  (let [action-map (reduce merge action-map action-maps)]
    (reduce-kv
      (fn [m k v]
        (assoc m (ns-abs ctx-ns k) v))
      {} action-map)))

;; should produce a sorted map
(defn- composite-reducer*
  [rmap]
  (let [visit-node
        (fn [[tag val]]
          (case tag
            :fn val
            :action-map (fn-map->reducer* val)
            :reducer-entry
            (let [{:keys [init actions ctx]} val
                  ctx-fn (if ctx (partial with-ctx ctx) identity)]
              (->> (ctx-fn actions)
                   (reducer init)))
            :reducer-map
            (composite-reducer* val)))]
    (-> (fn [m k node] (assoc m k (visit-node node)))
        (reduce-kv (sorted-map) rmap)
        (fn-map->reducer*))))

(defn composite-reducer
  [reducer-map & reducer-maps]
  (let [rmap (->> (reduce merge reducer-map reducer-maps)
                  (s/conform :readux/reducer-map))]
    (when (= rmap :cljs.spec/invalid)
      (rdu/error "Invalid input received"
                 (->> (reduce merge reducer-map reducer-maps)
                      (s/explain :readux/reducer-map))))
    (composite-reducer* rmap)))

(defn query-reg!
  [store query-id query-fn]
  {:pre [(spec/store*? store)
         (spec/kw? query-id)
         (spec/fun? query-fn)]}
  (-> (rds/store->queries store)
      (swap! update query-id query-reg!* query-fn query-id))
  nil)

(defn queries-reg!
  [store query-map]
  {:pre [(spec/store*? store)
         (spec/query-map? query-map)]}
  (doseq [[id qfn] query-map]
    (query-reg! store id qfn)))

(defn query
  ([store query-rq]
   (query store query-rq nil))
  ([store [query-id :as query-rq] path]
   {:pre [(spec/store*? store)
          (spec/kw? query-id)
          (spec/kw-coll? path {:label "path" :nil? true})]}
   (let [query-fn (-> store rds/store->queries deref (get query-id))]
     (assert (some? query-fn) (str "Query '" query-id "' is not registered with the store."))
     (-> (if path (reaction (get-in @(rds/store->model store) path))
                  (-> store rds/store->model))
         (query-fn query-rq)))))

(defn- ctx-dispatch
  "Yield dispatch function supporting contextual dispatches.

  When (connect)'ing components, a ns and a path into the state tree is
  supplied, this becomes the component context.

  Dispatches like '(dispatch {:type :foo})', i.e. where the action type key has
  no ns, are treated as contextual. That is, they are automatically prefixed
  the context-ns, ensuring other components in other contexts won't accidentally
  react to this action too.

  NOTE: Dispatches where the type key has a ns, e.g.
        '(dispatch {:type :some-ns/foo})', are treated as global and pass
        through unmodified."
  [store ns]
  {:pre [(spec/store*? store)
         (spec/kw-or-str? ns)]}
  (fn [action]
    (->> (if (-> action :type namespace nil?)
           (update action :type #(ns-abs ns %))
           action)
         (dispatch store))))

(defn- ctx-query
  "Yield query function supporting contextual queries.

  When (connect)'ing components, a ns and a path into the state tree is
  supplied, this becomes the component context.

  Queries like '(query [:counter-value])', i.e. where the query-id key has no
  ns, are treated as contextual. That is, the query automatically is provided
  the part of the state-tree which the component context manages.

  This makes queries automatically reusable across different (connect)'ed
  components.

  NOTE: Queries where the query-id has a NS, e.g.
        '(query [:some-ns/counter-value])' are treated as operating in the
        global context - that is, on the entire state tree."
  [store path]
  {:pre [(spec/store*? store)
         (spec/kw-coll? path {:label "path" :nil? true})]}
  (fn [[query-id :as query-rq]]
    (if (-> query-id namespace nil?)
      (query store query-rq path)
      (query store query-rq))))

(defn connect
  "Connect a (controller) component to a particular context.

  Controller components take two arguments, 'dispatch' & 'query' for dispatching
  actions and querying the state tree, respectively.
  The controller component then prepares the callbacks and reactions to pass
  along to a presentational component - which in turn only handles layout.

  '(connect)' wires up 'dispatch' and 'query' to work on the supplied store,
  and in the (optional) context provided.

  A context is a path into the state-tree and a key signifying the namespace of
  contextual actions/queries.

  In this way, the same controller component can be (connect)'ed multiple times
  to reuse the component(s) it manages by supplying different contexts.

  Likewise, multiple controller components can interoperate on the same slice
  of the state tree by (connect)'ing them to the same context."
  ([component store]
   {:pre [(spec/store*? store)]}
   (partial component
            (partial dispatch store)
            (partial query store)))
  ([component store ns]
   (connect component store ns nil))
  ([component store ns path]
   {:pre [(spec/store*? store)
          (spec/kw-coll? path {:label "path" :nil? true})]}
   (let [dispatch (ctx-dispatch store ns)
         query (ctx-query store path)]
     (partial component dispatch query))))