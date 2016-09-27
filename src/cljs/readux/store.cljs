(ns readux.store
  (:require [clojure.data :refer [diff]]
            [reagent.core :as r]
            [readux.utils :as rdu :include-macros true])
  (:require-macros [reagent.ratom :refer [reaction]]))

(def ^:private store-nil-msg "store argument was 'nil'")

(defn fsa-action?
  [action]
  (and (map? action) ((complement nil?) (:type action))))

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
  [store action]
  (let [model* (store->model* store)
        result ((store->reducer store) @model* action)]
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
    ([action]
     (assert
       (fsa-action? action)
       "actions must be FSA-compliant (https://github.com/acdlite/flux-standard-action)")
     ((:dispatch @store) store action))
    ([type payload]
     (assert
       (keyword? type) "expect action type to be a keyword value")
     ((:dispatch @store) {:type type :payload payload}))))

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

(defn log-model-diff
  "Store middleware to log model changes to console."
  [dispatch next model action]
  (let [{:keys [type payload]} action
        action-name (str (when-let [ns (namespace type)]
                           (str ns "/"))
                         (name type))]
    (rdu/log-group-collapsed
      (str "* Action['"  action-name "']")
      (when payload
        (rdu/log-group
          "Data"
          (rdu/log (rdu/ppstr payload))))
      (let [new-model (next model action)
            [removed added _] (diff model new-model)]
        (rdu/log-group
          "Added"
          (rdu/log (rdu/ppstr added)))
        (rdu/log-group
          "Removed"
          (rdu/log (rdu/ppstr removed)))
        new-model))))