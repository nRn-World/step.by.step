package app.stegvis.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsernameTest {
    @Test fun acceptsSwedishLettersAndTrims() {
        assertEquals("Åsa", Username.clean("  Åsa  "))
        assertEquals("ärlig.kalle", Username.clean("ärlig.kalle"))
        assertEquals("öster-1", Username.clean("öster-1"))
    }

    @Test fun rejectsLengthDoubleDotsAndBadEdges() {
        assertNull(Username.clean("ab"))
        assertNull(Username.clean("a".repeat(21)))
        assertNull(Username.clean("bo..x"))
        assertNull(Username.clean(".bob"))
        assertNull(Username.clean("bob_"))
        assertNull(Username.clean("Kalle Ö"))
    }

    @Test fun mapsToEmailLowerCaseWithLetterReplacements() {
        assertEquals("+asa@users.nrnworld.one", Username.toEmail("Åsa"))
        assertEquals("+bkalle@users.nrnworld.one", Username.toEmail("Äkalle".drop(1).let { "ä$it" }))
        assertEquals("+cnnie@users.nrnworld.one", Username.toEmail("Önnie"))
        assertEquals("anna.k@users.nrnworld.one", Username.toEmail("ANNA.k"))
    }

    @Test fun sameNameInAnyCaseIsSameAccount() {
        assertEquals(Username.toEmail("Anna-Maria"), Username.toEmail("anna-maria"))
    }
}
