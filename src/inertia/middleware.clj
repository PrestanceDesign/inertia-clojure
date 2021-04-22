(ns inertia.middleware
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [ring.util.response :as rr]))

(defn render
  [component props]
  (rr/response {:component component
                :props props}))

(defn- only-partial-data
  [{:keys [component props] :as inertia-data} request]
  (let [partial-data (rr/get-header request "x-inertia-partial-data")
        partial-component (rr/get-header request "x-inertia-partial-component")]
    (if (and partial-data (= component partial-component))
      (let [only (str/split partial-data #",")]
        (assoc inertia-data :props (select-keys props (map keyword only))))
      inertia-data)))

(defn wrap-inertia
  "Ring middleware for return either an HTTP or JSON response of a component to use
  with InertiaJS frontend integration."
  ([handler template asset-version]
   (wrap-inertia handler template asset-version {}))
  ([handler template asset-version share-props]
   (fn [request]
     (let [response (handler request)
           inertia-header (rr/get-header request "x-inertia")
           inertia-version (rr/get-header request "x-inertia-version")
           method (:request-method request)
           url (:uri request)]
       (if (and inertia-header (= method :get) (not= inertia-version asset-version))
         {:status 409
          :headers {"x-inertia-location" url}}
         (if (coll? (:body response))
             (let [inertia-data (-> response
                                    :body
                                    (update :props merge share-props)
                                    (only-partial-data request))
                   data-page (assoc inertia-data :url url :version asset-version)]
               (cond (= 302 (:status response)) response
                     inertia-header {:status 200
                                     :headers {"x-inertia" "true"
                                               "vary" "accept"}
                                     :body data-page}
                     :else (rr/response (template (json/write-value-as-string data-page)))))
             response))))))
