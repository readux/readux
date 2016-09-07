(ns readux.core)

(defmacro reducer-fn
  [input init action-map]
  (let [[model action args] input]
    `(fn [~model ~action ~args]
       (let [~model (or ~model ~init)]
         (case ~action
           ~@(reduce-kv (fn [vec k v] (conj vec k v)) [] action-map)
           ~model)))))