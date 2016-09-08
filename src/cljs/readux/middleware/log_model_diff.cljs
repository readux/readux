(ns readux.middleware.log-model-diff
  (:require [clojure.data :refer [diff]]
            [cljs.pprint :refer [pprint]])
  (:require-macros [readux.utils :as rdu]))

(defn log-model-diff
  [dispatch next model action data]
  (rdu/with-console-group
    (str "Action['" (name action) "']")
    (let [new-model (next model action data)
          [removed added _] (diff model new-model)]
      (rdu/with-console-group
        "Added"
        (pprint added))
      (rdu/with-console-group
        "Removed"
        (pprint removed))
      new-model)))