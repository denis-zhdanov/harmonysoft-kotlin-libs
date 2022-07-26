package tech.harmonysoft.oss.cucumber.glue

import com.mongodb.BasicDBObject
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import io.cucumber.datatable.DataTable
import io.cucumber.java.After
import io.cucumber.java.en.Given
import org.slf4j.Logger
import tech.harmonysoft.oss.common.data.DataProviderStrategy
import tech.harmonysoft.oss.mongo.config.TestMongoConfigProvider
import tech.harmonysoft.oss.mongo.constant.Mongo
import tech.harmonysoft.oss.test.util.VerificationUtil
import javax.inject.Inject

class MongoStepDefinitions {

    private val allDocumentsFilter = BasicDBObject()

    @Inject private lateinit var configProvider: TestMongoConfigProvider
    @Inject private lateinit var logger: Logger

    private val client: MongoClient by lazy {
        val config = configProvider.data
        val auth = config.credential?.let {
            "${it.login}:${it.password}@"
        } ?: ""
        MongoClients.create("mongodb://$auth${config.host}:${config.port}/${config.db}")
    }

    @After
    fun cleanUpData() {
        val db = client.getDatabase(configProvider.data.db)
        for (collectionName in db.listCollectionNames()) {
            logger.info("Deleting all documents from mongo collection {}", collectionName)
            val result = db.getCollection(collectionName).deleteMany(allDocumentsFilter)
            logger.info("Deleted {} document(s) in mongo collection {}", result.deletedCount, collectionName)
        }
    }

    @Given("^mongo ([^\\s]+) collection has the following documents?$")
    fun ensureDocumentExists(collection: String, data: DataTable) {
        for (documentData in data.asMaps()) {
            ensureDocumentExists(collection, documentData)
        }
    }

    fun ensureDocumentExists(collection: String, data: Map<String, Any>) {
        val filter = Filters.and(data.map {
            Filters.eq(it.key, it.value)
        })
        client.getDatabase(configProvider.data.db).getCollection(collection).updateOne(
            filter,
            Updates.set("dummy", "dummy"),
            UpdateOptions().upsert(true)
        )
    }

    @Given("^mongo ([^\\s]+) collection should have the following documents?$")
    fun verifyDocumentsExist(collection: String, data: DataTable) {
        for (expected in data.asMaps()) {
            verifyDocumentExists(collection, expected)
        }
    }

    fun verifyDocumentExists(collectionName: String, data: Map<String, Any>) {
        val collection = client.getDatabase(configProvider.data.db).getCollection(collectionName)
        val documents = collection
            .find(Mongo.Filter.ALL)
            .projection(Projections.include(data.keys.toList()))
            .toList()
            .map { it.toMap() }
        VerificationUtil.verifyContains(data, documents, data.keys, DataProviderStrategy.fromMap())
    }
}