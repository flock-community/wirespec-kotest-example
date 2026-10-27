package community.flock.library

import community.flock.wirespec.integration.kotest.extension.ChannelTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer

/** The [ChannelTransport] behind `.send()`: publishes every message with a plain Kafka producer. */
class KafkaChannelTransport(bootstrapServers: String) :
    ChannelTransport,
    AutoCloseable {

    private val producer = KafkaProducer(
        mapOf<String, Any>(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers),
        StringSerializer(),
        ByteArraySerializer(),
    )

    override suspend fun publish(topic: String, key: String?, body: ByteArray) {
        withContext(Dispatchers.IO) { producer.send(ProducerRecord(topic, key, body)).get() }
    }

    override fun close(): Unit = producer.close()
}
