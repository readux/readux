(ns readux.store
  (:require [clojure.data :refer [diff]]
            [reagent.core :as r]
            [readux.utils :as rdu :include-macros true]
            [readux.spec :as spec])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defn store->model*
  [store]
  {:pre [(spec/store*? store)]}
  (:model @store))

(defn store->model
  [store]
  {:pre [(spec/store*? store)]}
  (:model-ro @store))

(defn store->reducer
  [store]
  {:pre [(spec/store*? store)]}
  (:reducer @store))

(defn store->queries
  [store]
  {:pre [(spec/store*? store)]}
  (-> @store :queries))

(defn dispatch*
  ([store action]
   {:pre
    [(spec/action? action)]}
   ((@store :dispatch) store action))
  ([store type payload]
   {:pre
    [(rdu/spec-valid?
       ::spec/action.type type "Expect action type to be a keyword value")
     (rdu/spec-valid? ::spec/action.payload payload)]}
   (dispatch* store {:type type :payload payload})))

;; default end-of-chain dispatch fn
(defn dispatch-core
  [store action]
  {:pre [(spec/store*? store)
         (spec/action? action)]}
  (let [model* (store->model* store)
        result ((store->reducer store) @model* action)]
    (assert (some? result) "Root reducer returned 'nil' - result of reduce *must* be some new (non-nil) state!")
    (reset! model* result)
    nil))

(defn ->store*
  [reducer]
  {:pre [(spec/fun? reducer)]}
  (let [model (r/atom nil)]
    (atom {:model model
           :model-ro (reaction @model)
           :reducer reducer
           :queries (r/atom {})
           :dispatch dispatch-core})))

(defn apply-mw
  [& middleware]
  (fn do-apply-mw
    [store]
    {:pre [(spec/store*? store)]}
    (let [dispatch-fn (partial dispatch* store)
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
  {:pre [(spec/fun? dispatch)
         (spec/fun? next)
         (spec/action? action)]}
  (let [{:keys [type payload meta]} action
        action-name (str (when-let [ns (namespace type)]
                           (str ns "/"))
                         (name type))]
    (if (get-in meta [:log-model-diff :nolog])
      ;; no logging for this action
      (do (-> (str "%c* Action['"  action-name "'] -- logging skipped")
              (rdu/log "color:blue; font-weight: bold"))
          (next model action))
      ;; log the action
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
          new-model)))))