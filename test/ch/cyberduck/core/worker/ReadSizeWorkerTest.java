package ch.cyberduck.core.worker;

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.Path;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

/**
 * @version $Id$
 */
public class ReadSizeWorkerTest extends AbstractTestCase {

    @Test
    public void testRun() throws Exception {
        final Path d = new Path("/d", EnumSet.of(Path.Type.directory));
        d.attributes().setSize(-1L);
        final ReadSizeWorker worker = new ReadSizeWorker(Arrays.asList(d)) {
            @Override
            public void cleanup(final Long result) {
                //
            }
        };
        assertEquals(0L, worker.run(), 0L);
    }
}