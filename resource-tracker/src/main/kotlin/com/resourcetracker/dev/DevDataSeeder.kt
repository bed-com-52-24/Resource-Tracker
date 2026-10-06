import com,resourcetracker.user.*
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** for local development */

@Component
@Profile("dev")
class DevDataSeeder(private val users: UserRepository,
private val userService: UserService): ApplicationRunner
{
    override fun run(args: ApplicationArguments){
        if(!users.existsByUsername("admin")){
            userService.create("admin","admin@example.com", "Dev Admin", "Admin@2026", Role.ADMIN, "IT", setOf("Platform"))
        }
    }
}