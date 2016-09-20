(ns readux.core
  (:require [reagent.core :as r]
            [readux.store :as rds])
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
  (if (-> kw namespace nil?)
    (keyword (name ns) (name kw))
    kw))

;; ----
(defn dispatch
  ([store action]
   (assert
     (rds/fsa-action? action)
     "actions must be FSA-compliant (https://github.com/acdlite/flux-standard-action)")
   ((@store :dispatch) store action))
  ([store type payload]
   (assert (keyword? type) "Expect action type to be a keyword value")
   (dispatch store {:type type :payload payload})))

(defn store
  ([reducer] (store reducer identity))
  ([reducer enhancer]
   (let [store (enhancer (rds/->store* reducer))]
     (dispatch store {:type :readux/init})
     store)))

(defn composite-reducer
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
  [reducer-map]
  (fn [model action]
    (->> (for [[path reducer] reducer-map]
           [path (reducer (get model path) action)])
         (into {}))))

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
  [init & action-maps]
  (let [action-map (reduce merge action-maps)]
    (fn [model action]
      (let [model (or model init)]
        (if-let [handler (->> action :type (get action-map))]
          (handler model action)
          model)))))

(defn query-reg!
  [store query-id query-fn]
  (assert (keyword? query-id))
  (-> (rds/store->queries store)
      (swap! update query-id query-reg!* query-fn query-id)))

(defn queries-reg!
  [store query-map]
  (doseq [[id qfn] query-map]
    (query-reg! store id qfn)))

(defn query
  ([store query-rq]
   (query store query-rq nil))
  ([store [query-id :as query-rq] path]
   (assert (keyword? query-id) "Query key must always be a keyword")
   (assert (some? #(% path)) "path must be nil or a vector")
   (let [query-fn (-> store rds/store->queries deref (get query-id))]
     (assert (some? query-fn) (str "Query '" query-id "' is not registered with the store."))
     (-> (if path (reaction (get-in @(rds/store->model store) path))
                  (-> store rds/store->model))
         (query-fn query-rq)))))

;; ---- contextualising components
(defn with-ctx
  "Modify (relative) actions in action map to use supplied context ns."
  [ctx-ns & action-maps]
  (reduce-kv
    (fn [m k v]
      (assoc m (ns-abs ctx-ns k) v))
    {} (reduce merge action-maps)))

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
   (partial component
            (partial dispatch store)
            (partial query store)))
  ([component store ns]
   (connect component store ns nil))
  ([component store ns path]
   (let [dispatch (ctx-dispatch store ns)
         query (ctx-query store path)]
     (partial component dispatch query))))