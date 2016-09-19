(ns readux.core
  (:require [reagent.core :as r]
            [readux.store :as rds])
  (:require-macros [reagent.ratom :refer [reaction]]))

;; Internal
(defn- query-reg!*
  [existing-fn new-fn query-id]
  (when-not (nil? existing-fn)
    (->> (str "Overriding existing query '" query-id "'!")
         (.warn js/console)))
  new-fn)

;; Interface
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
     (dispatch store {:type :READUX/INIT})
     store)))

(defn composite-reducer
  [reducer-map]
  (fn [model action]
    (->> (for [[path reducer] reducer-map]
           [path (reducer (get model path) action)])
         (into {}))))

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
   (.warn js/console "! query call")
   (assert (keyword? query-id) "Query key must always be a keyword")
   (assert (some? #(% path)) "path must be nil or a vector")
   (let [query-fn (-> store rds/store->queries deref (get query-id))]
     (assert (some? query-fn) (str "Query '" query-id "' is not registered with the store."))
     (-> (if path (do (.warn js/console "QUERY IS CONTEXTUAL")
                      (reaction (get-in @(rds/store->model store) path))
                      #_(-> store rds/store->model (get-in path) (reaction)))
                  (-> store rds/store->model))
         (query-fn query-rq)))))