(ns readux.core)

(defmacro reducer-fn
  [input init action-map]
  (let [[model action] input]
    `(fn [~model ~action]
       (let [~model (or ~model ~init)]
         (case (:type ~action)
           ~@(reduce-kv (fn [vec k v] (conj vec k v)) [] action-map)
           ~model)))))