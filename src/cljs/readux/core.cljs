(ns readux.core
  (:require [reagent.core :as r]
            [readux.store :as rs])
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
  [store action & args]
  ((@store :dispatch) store action args))

(defn store
  ([reducer] (store reducer identity))
  ([reducer enhancer]
   (let [store (enhancer (rs/->store* reducer))]
     (dispatch store :READUX/INIT)
     store)))

(defn composite-reducer
  [reducer-map]
  (fn [model action & args]
    (->> (for [[path reducer] reducer-map]
           [path (apply reducer (get model path) action args)])
         (into {}))))

(defn query-reg!
  [store query-id query-fn]
  (assert (keyword? query-id))
  (-> (rs/store->queries store)
      (swap! update query-id query-reg!* query-fn query-id)))

(defn query
  [store [query-id :as query-rq]]
  (assert (keyword? query-id))
  (let [query-fn (-> store rs/store->queries deref (get query-id))]
    (assert (some? query-fn) (str "Query '" query-id "' not registered with store"))
    (query-fn (rs/store->model store) query-rq)))