package sg.com.quantai.middleware.configs

import org.casbin.jcasbin.main.Enforcer
import org.casbin.jcasbin.main.SyncedEnforcer
import org.casbin.jcasbin.persist.file_adapter.FileAdapter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.File
import java.nio.file.Files

@Configuration
class CasbinConfig {

    @Bean
    fun enforcer(): Enforcer {
        val modelResource  = ClassPathResource("casbin/model.conf")
        val policyResource = ClassPathResource("casbin/policy.csv")

        val tempDir = Files.createTempDirectory("casbin").toFile()
        tempDir.deleteOnExit()

        val modelFile  = File(tempDir, "model.conf")
        val policyFile = File(tempDir, "policy.csv")

        modelResource.inputStream.use  { Files.copy(it, modelFile.toPath()) }
        policyResource.inputStream.use { Files.copy(it, policyFile.toPath()) }

        // SyncedEnforcer is thread-safe — safe for concurrent request handling
        return SyncedEnforcer(modelFile.absolutePath, FileAdapter(policyFile.absolutePath))
    }
}
