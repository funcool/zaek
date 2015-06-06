;; Copyright (c) 2015 Andrey Antukh <niwi@niwi.nz>
;; All rights reserved.
;;
;; Redistribution and use in source and binary forms, with or without
;; modification, are permitted provided that the following conditions are met:
;;
;; * Redistributions of source code must retain the above copyright notice, this
;;   list of conditions and the following disclaimer.
;;
;; * Redistributions in binary form must reproduce the above copyright notice,
;;   this list of conditions and the following disclaimer in the documentation
;;   and/or other materials provided with the distribution.
;;
;; THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
;; AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
;; IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
;; DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
;; FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
;; DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
;; SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
;; CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
;; OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
;; OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

(ns zaek.core
  "Lightweight RabbitMQ (AMQP) client."
  (:require [clojure.walk :refer [stringify-keys keywordize-keys]]
            [zaek.consumer :as consumer])
  (:import com.rabbitmq.client.Connection
           com.rabbitmq.client.ConnectionFactory
           com.rabbitmq.client.AMQP$BasicProperties
           com.rabbitmq.client.AMQP$Queue$DeclareOk
           com.rabbitmq.client.Consumer
           com.rabbitmq.client.Channel))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants & Defaults
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^{:dynamic true
       :doc "Default connection options."}
  *default-options*
  {:username "guest"
   :password "guest"
   :vhost "/"
   :host "localhost"
   :heartbeat ConnectionFactory/DEFAULT_HEARTBEAT
   :timeout ConnectionFactory/DEFAULT_CONNECTION_TIMEOUT
   :port ConnectionFactory/DEFAULT_AMQP_PORT
   :recovery-interval 5000 ;; 5s
   :topology-recovery true})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Types
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype DefaultConnection [^Connection conn]
  java.lang.AutoCloseable
  (close [_]
    (.close conn)))

(deftype DefaultChannel [^Channel chan]
  java.lang.AutoCloseable
  (close [_]
    (.close chan)))

(deftype DefaultQueue [^AMQP$Queue$DeclareOk internal]
  clojure.lang.Named
  (getNamespace [_]
    "zaek.core")
  (getName [_]
    (.getQueue internal))

  Object
  (toString [_]
    (.getQueue internal)))

(alter-meta! #'->DefaultConnection assoc :private true)
(alter-meta! #'->DefaultChannel assoc :private true)
(alter-meta! #'->DefaultQueue assoc :private true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn connect*
  "Create a new RabbitMQ connection.

  This is a low level function for create connection
  and it does not uses any defaults, so if you are using
  this function you should pass all declared parameters
  because they are mandatory."
  {:no-doc true :version "0.1"}
  [{:keys [host port username password vhost heartbeat timeout
           recovery-interval topology-recovery]
    :as options}]
  (let [cfactory (ConnectionFactory.)]
    (doto cfactory
      (.setUsername ^String username)
      (.setPassword ^String password)
      (.setHost ^String host)
      (.setPort ^long port)
      (.setVirtualHost ^String vhost)
      (.setRequestedHeartbeat ^long heartbeat)
      (.setConnectionTimeout ^long timeout)
      (.setNetworkRecoveryInterval ^long recovery-interval)
      (.setTopologyRecoveryEnabled ^bool topology-recovery))
    (DefaultConnection. (.newConnection cfactory))))

(defn connect
  "Create a new RabbitMQ connection.

  The default values will be used for options that are
  not provided. You may call this funcion without arguments
  and default options will be used for create the connection."
  {:version "0.1"}
  ([] (connect {}))
  ([options] (connect* (merge *default-options* options))))

(defn channel
  "Create new amqp channel on given connection.

  This function returns nil if the maximum channels number
  is reached. An optionally options can be passed for
  preconfigure the channel on creation time.

  Possible options:
  - `:prefetch`: request a specific prefetchCount Qos settings
                 for this channel.
  "
  {:version "0.1"}
  ([connection]
   (channel connection {}))
  ([connection {:keys [prefetch] :or {prefetch 1}}]
   (let [^Connection conn (.-conn ^DefaultConnection connection)
         ^Channel chan (.createChannel conn)]
     (when prefetch
       (.basicQos chan prefetch))
     (DefaultChannel. chan))))

(defn declare-exchange!
  "Declare an exchange with given name and
  optionally given options."
  {:version "0.1"}
  ([channel exchange]
   (declare-exchange! channel exchange {}))
  ([channel exchange {:keys [type durable autodelete internal]
                      :or {type :direct durable false autodelete true internal false}}]
   (let [^String exchange' (name exchange)
         ^String type (name type)
         ^Channel chan (.-chan ^DefaultChannel channel)]
     (.exchangeDeclare chan exchange' type durable autodelete internal {})
     exchange)))

(defn declare-queue!
  "Declare a server-named or named queue."
  {:version "0.1"}
  ([channel]
   (let [chan (.-chan ^DefaultChannel channel)
         result (.queueDeclare ^Channel chan)]
     (DefaultQueue. result)))
  ([channel name]
   (declare-queue! channel name {}))
  ([channel name' {:keys [durable exclusive autodelete]
                  :or {exclusive true durable false autodelete true}}]
   (let [chan (.-chan ^DefaultChannel channel)
         result (.queueDeclare ^Channel chan
                               ^String (name name')
                               ^Boolean durable
                               ^Boolean exclusive
                               ^Boolean autodelete
                               {})]
     (DefaultQueue. result))))

(defn bind-queue!
  "Bind queue to an exchange using given
  routing key."
  {:version "0.1"}
  [channel queue exchange routingkey]
  (let [^Channel chan (.-chan ^DefaultChannel channel)
        ^String queue (name queue)
        ^String exchange (name exchange)
        ^String routingkey (name routingkey)]
    (.queueBind chan queue exchange routingkey)
    channel))

(defn publish
  {:version "0.1"}
  ([channel exchange routingkey data]
   (publish channel exchange routingkey data {}))
  ([channel exchange routingkey data {:keys [mandatory] :or {mandatory false} :as options}]
   (let [^Channel chan (.-chan ^DefaultChannel channel)
         ^AMQP$BasicProperties props (consumer/build-props options)
         ^String exchange (name exchange)
         ^String routingkey (name routingkey)]
     (.basicPublish chan exchange routingkey mandatory props data))))

(defn consume
  {:version "0.1"}
  ([channel queue callback]
   (when-not (satisfies? consumer/IConsumer callback)
     (throw (IllegalArgumentException. "the callback should implement IConsumer protocol.")))
   (let [^Channel chan (.-chan ^DefaultChannel channel)
         ^String queue (name queue)]
     (.basicConsume chan queue (consumer/adapt callback channel))))
  ([channel queue autoack tag callback]
   (when-not (satisfies? consumer/IConsumer callback)
     (throw (IllegalArgumentException. "the callback should implement IConsumer protocol.")))
   (let [^Channel chan (.-chan ^DefaultChannel channel)]
     (.basicConsume chan
                    ^String (name queue)
                    ^Boolean autoack
                    ^String (name tag)
                    ^Consumer (consumer/adapt callback channel)))))

(defn ack
  "Acknowledge a recevied message by delivery tag."
  {:version "0.1"}
  ([channel tag]
   (ack channel tag false))
  ([channel tag multiple]
   (let [^Channel chan (.-chan ^DefaultChannel channel)
         ^String deliverytag (name tag)]
     (.basicAck chan deliverytag multiple))))

(defn nack
  "Acknowledge a recevied message by delivery tag."
  {:version "0.1"}
  ([channel tag]
   (nack channel tag false false))
  ([channel tag requeue]
   (nack channel tag requeue false))
  ([channel tag requeue multiple]
   (let [^Channel chan (.-chan ^DefaultChannel channel)
         ^String deliverytag (name tag)]
     (.basicNack chan deliverytag multiple requeue))))

(defn cancel
  "Cancel a consumer by consumer tag."
  {:version "0.1"}
  [channel tag]
  (let [^Channel chan (.-chan ^DefaultChannel channel)
        ^String consumertag (name tag)]
    (.basicCancel chan consumertag)))

(defn close
  "A polymorphic function for close resources.

  It admits any object that implements AutoCloseable
  java interface, such as Connection, Channel among
  others."
  {:version "0.1"}
  [^java.lang.AutoCloseable o]
  (.close o))
