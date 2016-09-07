(ns readux.store
  (:require [reagent.core :as r])
  (:require-macros [reagent.ratom :refer [reaction]]))

(def ^:private store-nil-msg "store argument was 'nil'")

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