(defproject skachat "0.1.0"

  :description "Web-site example.com"
  :url "http://example.com"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [selmer "1.0.4"]
                 [luminus/config "0.5"]
                 [ring-middleware-format "0.7.0"]
                 [metosin/ring-http-response "0.6.5"]
                 [bouncer "1.0.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [compojure "1.4.0"]
                 [ring-webjars "0.1.1"]
                 [ring/ring-defaults "0.1.5"]
                 [ring "1.4.0" :exclusions [ring/ring-jetty-adapter]]
                 [mount "0.1.8"]
                 [luminus-nrepl "0.1.2"]
                 [buddy "0.10.0"]
                 [org.webjars/webjars-locator-jboss-vfs "0.1.0"]
                 [luminus-immutant "0.1.0"]
                 [net.coobird/thumbnailator "0.4.8"]
                 [sitemap "0.2.5"]
                 [clj-rss "0.2.3"]
                 [luminus-log4j "0.1.2"]
                 [com.datomic/datomic-free "0.9.5350"
                  :exclusions [org.slf4j/slf4j-nop org.slf4j/log4j-over-slf4j]]
                 [io.rkn/conformity "0.4.0"]
                 [com.cemerick/url "0.1.1"]]

  :min-lein-version "2.0.0"
  :uberjar-name "skachat.jar"
  :jvm-opts ["-server" "-Dclojure.compiler.direct-linking=true"]
  :resource-paths ["resources"]

  :main skachat.core

  :plugins [[lein-environ "1.0.1"]
            [lein-asset-minifier "0.2.8"]]

  :minify-assets
    {:assets
      {"resources/public/css/site.min.css" ["resources/public/css"]}}

  :profiles
  {:uberjar {:omit-source true
             :env {:production true}
             :aot :all
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources"]}
   :dev           [:project/dev :profiles/dev]
   :test          [:project/test :profiles/test]
   :project/dev  {:dependencies [[prone "1.0.1"]
                                 [ring/ring-mock "0.3.0"]
                                 [ring/ring-devel "1.4.0"]
                                 [pjstadig/humane-test-output "0.7.1"]]


                  :source-paths ["env/dev/clj"]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]
                  ;;when :nrepl-port is set the application starts the nREPL server on load
                  :env {:dev        true
                        :nrepl-port 7000}}
   :project/test {:env {:test       true
                        :port       3001
                        :nrepl-port 7001}}
   :profiles/dev {}
   :profiles/test {}})
