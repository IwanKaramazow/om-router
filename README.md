# Om-Router


### Usage

```clojure
;; 0. Don't forget to require the router...
(ns om-router.example
  (:require [om-router.core :as router]))
;; 1. define some routes
(def routes
  {"/" {:handler :app
        :index :home ;;index-route for "/"
        :children {"about" {:handler :about}
                   "users/:id" {:handler :user} ;; :id -> available as params ;)
                   "content" {:redirect "/about"} ;; redirect to /about
                   "*" {:handler :not-found}}}})

;; 2. add the routes to your initial app-state
(def app-state {:routes routes 
                :something/else "rest of your app"})

;; 3. Hook up the router to your parser
(defmulti read om/dispatch)

;; hook up the router's read to `:router`
(defmethod read :router
  [env key params]
  (router/read env key params))

(defmulti mutate om/dispatch)

;; hoop up the router's mutation to 'router/transact
(defmethod mutate 'router/transact
  [env key params]
  (router/mutate env key params))

;; 4. define a multimethod to help the router find components
(defmulti find-component router/dispatch)

;; 5. Write some components
(defui App
  static om/IQuery
  (query [this]
         [{:router [:route/pathname]} ;;rest of your query..
          ]))
(render [this]
        (let [{{:keys [route/pathname]} :router} (:app (om/props this))]
          (dom/div nil
                   (dom/h1 nil "App")
                   (dom/div nil "pathname: " pathname))))
;; rest of your components

;; 6. This is important, the keys you defined under `:handler` in the route config
;; are necessary to help find the components. When matching the routes, the router will dispatch for example on :app to find component `App` as defined below.
(defmethod find-component :app [_] App)

;; define multimethods for the rest of your components, e.g.
(defn not-found []
  (dom/h1 nil "This is the not-found page"))

(defmethod find-component :not-found [_] not-found)

;;7. Define your reconciler
(defonce reconciler
  (om/reconciler
   {:state app-state
    :parser (om/parser {:read read :mutate mutate})}))

;;8. jack a Router component with a dispatch multimethod into the reconciler & fire the thing up
(om/add-root!
 reconciler (router/Router {:dispatch find-component})
 (gdom/getElement "app"))

```
