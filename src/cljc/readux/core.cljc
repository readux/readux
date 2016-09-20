(ns readux.core)

(defmacro actions
  [action-map]
  (let [am (reduce-kv
             (fn [m k v]
               (assoc m k `(fn [~'model ~'action] ~v)))
             {} action-map)]
    `~am))