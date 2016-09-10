(ns readux.middleware.log-model-diff
  (:require [clojure.data :refer [diff]]
            [cljs.pprint :refer [pprint]])
  (:require-macros [readux.utils :as rdu]))

(defn- ppstr
  [obj]
  (with-out-str (cljs.pprint/pprint obj)))

(defn- log
  [& args]
  (.apply (.-log js/console) js/console (into-array args)))

(defn- warn
  [& args]
  (.apply (.-warn js/console) js/console (into-array args)))

(defn- error
  [& args]
  (.apply (.-error js/console) js/console (into-array args)))

(defn log-model-diff
  [dispatch next model action data]
  (rdu/with-console-group
    (str "Action['" (name action) "']")
    (when data
      (rdu/with-console-group
        "Data"
        (log (ppstr data))))
    (let [new-model (next model action data)
          [removed added _] (diff model new-model)]
      (rdu/with-console-group
        "Added"
        (log (ppstr added)))
      (rdu/with-console-group
        "Removed"
        (log (ppstr removed)))
      new-model)))