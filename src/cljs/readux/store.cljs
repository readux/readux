(ns readux.store
  (:require [reagent.core :as r])
  (:require-macros [reagent.ratom :refer [reaction]]))

(def ^:private store-nil-msg "store argument was 'nil'")


;; ((apply comp (mapv #(partial % 1 2) [foo foo2])) 3)
;; => 9

#_(defn mock-dispatch
  [model action args]
  (println (str "DISPATCH(" action "," args ")")))

#_(defn mock-reducer
  [model action args]
  (println "mock reducer hit")
  (update model :val inc))

#_(defn testmw1
  [dispatch next model action args]
  (println "testmw1 pre")
  (let [r (next model action args)]
    (println "testmw1 post")
    r))

#_(defn testmw2
  [dispatch next model action args]
  (println "testmw2 pre")
  (let [r (next model action args)]
    (dispatch action args)
    (println "testmw2 post")
    r))

#_(defn apply-mw
  [reducer & mw-fns]
  (->> mw-fns
       reverse
       (mapv #(partial % mock-dispatch))
       (reduce (fn [next mw] (partial mw next)) mock-reducer)))

(defn store->model*
  [store]
  (assert (some? store) store-nil-msg)
  (:model @store))

(defn store->model
  [store]
  (assert (some? store) store-nil-msg)
  (:model-ro @store))

(defn store->reducer
  [store]
  (assert (some? store) store-nil-msg)
  (:reducer @store))

(defn store->queries
  [store]
  (assert (some? store) store-nil-msg)
  (-> @store :queries))

(defn dispatch-core
  [store action args]
  (let [model* (store->model* store)
        result ((store->reducer store) @model* action args)]
    (assert (some? result) "Root reducer returned 'nil' - result of reduce *must* be some new (non-nil) state!")
    (reset! model* result)
    nil))

(defn ->store*
  [reducer]
  (let [model (r/atom nil)]
    (atom {:model model
           :model-ro (reaction @model)
           :reducer reducer
           :queries (r/atom {})
           :dispatch dispatch-core})))

(defn- mw-dispatcher
  [store]
  (fn
    ([action] ((:dispatch @store) store action nil))
    ([action data]
      ((:dispatch @store) store action data))))

(defn apply-mw
  [& middleware]
  (fn do-apply-mw
    [store]
    (let [dispatch-fn (mw-dispatcher store)
          reducer (store->reducer store)]
      (->> middleware
           reverse
           (mapv #(partial % dispatch-fn))
           (reduce (fn [next mw] (partial mw next)) reducer)
           (swap! store assoc :reducer))
      store)))