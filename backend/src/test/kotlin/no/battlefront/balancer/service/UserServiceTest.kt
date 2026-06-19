package no.battlefront.balancer.service

import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional

class UserServiceTest {
    private val userRepository = mock(UserRepository::class.java)
    private val service = UserService(userRepository)

    @Test
    fun `listUsers returns sorted list`() {
        val users =
            listOf(
                User(id = 1L, username = "charlie", password = "pw", role = "supervisor"),
                User(id = 2L, username = "alice", password = "pw", role = "admin"),
                User(id = 3L, username = "bob", password = "pw", role = "editor"),
            )
        `when`(userRepository.findAll()).thenReturn(users)

        val result = service.listUsers()
        assertEquals(3, result.size)
        // sorted by role then username: admin < editor < supervisor
        assertEquals("admin", result[0].role)
        assertEquals("editor", result[1].role)
        assertEquals("supervisor", result[2].role)
    }

    @Test
    fun `updateUserRole succeeds for non-admin target`() {
        val target = User(id = 2L, username = "bob", password = "pw", role = "editor")
        `when`(userRepository.findById(2L)).thenReturn(Optional.of(target))
        `when`(userRepository.save(target)).thenReturn(target)

        val result = service.updateUserRole(targetId = 2L, newRole = "supervisor", currentUserId = 1L)
        assertEquals("supervisor", result.role)
        assertEquals("bob", result.username)
    }

    @Test
    fun `updateUserRole rejects admin role assignment`() {
        assertThrows<IllegalArgumentException> {
            service.updateUserRole(targetId = 2L, newRole = "admin", currentUserId = 1L)
        }
    }

    @Test
    fun `updateUserRole rejects self-modification`() {
        assertThrows<IllegalArgumentException> {
            service.updateUserRole(targetId = 1L, newRole = "editor", currentUserId = 1L)
        }
    }

    @Test
    fun `updateUserRole rejects changing another admin`() {
        val adminTarget = User(id = 3L, username = "alice", password = "pw", role = "admin")
        `when`(userRepository.findById(3L)).thenReturn(Optional.of(adminTarget))

        assertThrows<IllegalArgumentException> {
            service.updateUserRole(targetId = 3L, newRole = "editor", currentUserId = 1L)
        }
    }

    @Test
    fun `updateUserRole rejects unknown role`() {
        assertThrows<IllegalArgumentException> {
            service.updateUserRole(targetId = 2L, newRole = "unknown", currentUserId = 1L)
        }
    }

    @Test
    fun `deleteUser succeeds for non-admin target`() {
        val target = User(id = 2L, username = "bob", password = "pw", role = "editor")
        `when`(userRepository.findById(2L)).thenReturn(Optional.of(target))

        service.deleteUser(targetId = 2L, currentUserId = 1L)

        org.mockito.Mockito
            .verify(userRepository)
            .delete(target)
    }

    @Test
    fun `deleteUser rejects self-deletion`() {
        assertThrows<IllegalArgumentException> {
            service.deleteUser(targetId = 1L, currentUserId = 1L)
        }
    }

    @Test
    fun `deleteUser rejects deleting an admin`() {
        val adminTarget = User(id = 3L, username = "alice", password = "pw", role = "admin")
        `when`(userRepository.findById(3L)).thenReturn(Optional.of(adminTarget))

        assertThrows<IllegalArgumentException> {
            service.deleteUser(targetId = 3L, currentUserId = 1L)
        }
    }
}
