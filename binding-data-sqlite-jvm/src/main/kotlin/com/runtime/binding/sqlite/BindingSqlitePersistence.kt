package com.runtime.binding.sqlite

import com.runtime.binding.CredentialRefRepository
import com.runtime.binding.ModelBindingRepository
import com.runtime.binding.ModelProfileRepository
import com.runtime.binding.ProviderDefinitionRepository

/**
 * SQLite-backed repositories for Prompt 03. [dataSource] is visible for tests/seeding in the same module.
 */
class BindingSqlitePersistence(databasePath: String) {
    internal val dataSource = BindingSqliteDataSource(databasePath)

    /** Raw JDBC for tests or migrations; prefer repositories for app code. */
    fun withConnection(block: (java.sql.Connection) -> Unit) {
        dataSource.withConnection(block)
    }

    val providerDefinitionRepository: ProviderDefinitionRepository =
        SqliteProviderDefinitionRepository(dataSource)
    val credentialRefRepository: CredentialRefRepository =
        SqliteCredentialRefRepository(dataSource)
    val modelProfileRepository: ModelProfileRepository =
        SqliteModelProfileRepository(dataSource)
    val modelBindingRepository: ModelBindingRepository =
        SqliteModelBindingRepository(dataSource)

    fun close() {
        dataSource.close()
    }
}
