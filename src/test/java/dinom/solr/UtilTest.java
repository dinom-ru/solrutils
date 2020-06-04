package dinom.solr;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Yegor Kozlov
 */
public class UtilTest {
    @Test
    public void testToTagId(){
        assertEquals("john-dow", Util.toTagId("John Dow"));
        assertEquals("-john--dow-", Util.toTagId(" John  Dow "));
        assertEquals("working-with-the-ceo-board-c-suite", Util.toTagId("Working with the CEO/Board/C-Suite"));
        assertEquals("r&d-portfolio-management", Util.toTagId("R&D Portfolio Management"));
        assertEquals("research-and-development/r&d-portfolio-management", Util.toTagId("Research and Development: R&D Portfolio Management"));
        assertEquals("research-and-development/key-initiative/r&d-portfolio-management",
                Util.toTagId("Research and Development: R&D Portfolio Management", ": ", "/key-initiative/"));
    }
}
