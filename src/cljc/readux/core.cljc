(ns readux.core)

(defmacro defactions
  [label action-map]
  (let [am (reduce-kv
             (fn [m k v]
               (assoc m k `(fn [~'model ~'action] ~v)))
             {} action-map)]
    `(def ~label ~am)))