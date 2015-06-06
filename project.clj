(defproject funcool/zaek "0.1.0-SNAPSHOT"
  :description "Lightweight RabbitMQ (AMQP) client."
  :url "https://github.com/funcool/zaek"
  :license {:name "BSD (2-Clause)"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :javac-options ["-target" "1.7" "-source" "1.7" "-Xlint:-options"]
  :dependencies [[org.clojure/clojure "1.6.0" :scope "provided"]
                 [com.rabbitmq/amqp-client "3.5.3"]]
  :profiles {:dev {:global-vars {*warn-on-reflection* true}}})

