package io.aubergine.ambit.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.aubergine.ambit.data.db.AppDatabase
import io.aubergine.ambit.data.db.DatabaseKey
import io.aubergine.ambit.data.db.EncryptedDatabase
import io.aubergine.ambit.data.db.Migrations
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .apply {
                // Encrypted with SQLCipher under a Keystore-wrapped key. If the Keystore is
                // unusable on this device the database stays plaintext rather than unopenable.
                runCatching { EncryptedDatabase.factory(context, DatabaseKey(context)) }
                    .getOrNull()
                    ?.let { openHelperFactory(it) }
            }
            // No destructive fallback, ever. Users keep months of observations in here and
            // a wiped log on upgrade is indistinguishable from data loss.
            .addMigrations(*Migrations.ALL)
            .build()
}
