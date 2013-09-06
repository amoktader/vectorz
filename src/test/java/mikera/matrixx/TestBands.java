package mikera.matrixx;

import static org.junit.Assert.*;
import mikera.matrixx.impl.DiagonalMatrix;
import mikera.vectorz.Vector;
import mikera.vectorz.Vectorz;

import org.junit.Test;

public class TestBands {
	@Test public void testFilledMatrix() {
		Matrix m=Matrix.create(3, 3);
		Vectorz.fillIndexes(m.asVector());
		
		assertEquals(2,m.upperBandwidth());
		assertEquals(2,m.upperBandwidthLimit());
		assertEquals(2,m.lowerBandwidth());
		assertEquals(2,m.lowerBandwidthLimit());
		
		assertEquals(Vector.of(1,5),m.getBand(1));
		assertEquals(Vector.of(6),m.getBand(-2));

	}
	
	@Test public void testDiagonalMatrix() {
		DiagonalMatrix m=DiagonalMatrix.create(Vector.of(0,1,2));
		
		assertEquals(0,m.upperBandwidth());
		assertEquals(0,m.upperBandwidthLimit());
		assertEquals(0,m.lowerBandwidth());
		assertEquals(0,m.lowerBandwidthLimit());
	}
	
	@Test public void testBandLength() {
		Matrix m=Matrix.create(3, 4);
		
		assertEquals(3,m.upperBandwidthLimit());
		assertEquals(2,m.lowerBandwidthLimit());
		
		assertEquals(0,m.bandLength(4));
		assertEquals(1,m.bandLength(3));
		assertEquals(2,m.bandLength(2));
		assertEquals(3,m.bandLength(1));
		assertEquals(3,m.bandLength(0));
		assertEquals(2,m.bandLength(-1));
		assertEquals(1,m.bandLength(-2));
		assertEquals(0,m.bandLength(-3));
	}
}