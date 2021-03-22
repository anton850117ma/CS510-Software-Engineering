import org.junit.*;
import static org.junit.Assert.*;

import java.io.PrintStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

public class TestM {

	/* add your test code here */
	private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
	private final PrintStream originalOut = System.out;

	@Before
	public void setUpStreams() {
		System.setOut(new PrintStream(outContent));
	}
	
	@After
	public void restoreStreams() {
		System.setOut(originalOut);
	}

	//(1) satisfy node coverage but not edge coverage
	@Test
	public void first() {

		M tester = new M();
		String newline = System.getProperty("line.separator");

		//test set:
		tester.m("gg", 1);	//cover [1,2,4,8,7,9,11]
		String str1 = outContent.toString();
		outContent.reset();

		tester.m("g", 0);	//cover [1,3,4,6,9,11]
		String str2 = outContent.toString();
		outContent.reset();

		tester.m("", 0);	//cover [1,3,4,5,9,10]
		String str3 = outContent.toString();
		
		// cover all nodes but does not cover edge (4,7)
		String str4 = "b" + newline + "a"+ newline + "zero" + newline;
		assertEquals(str4, str1 + str2 + str3);
	}

	//(2) satisfy edge coverage but not edge-pair coverage
	@Test
	public void second() {

		M tester = new M();
		String newline = System.getProperty("line.separator");

		//test set:
		tester.m("ggg", 0);	//cover [1,3,4,7,9,11]
		String str0 = outContent.toString();
		outContent.reset();

		tester.m("gg", 1);	//cover [1,2,4,8,7,9,11]
		String str1 = outContent.toString();
		outContent.reset();

		tester.m("g", 0);	//cover [1,3,4,6,9,11]
		String str2 = outContent.toString();
		outContent.reset();

		tester.m("", 0);	//cover [1,3,4,5,9,10]
		String str3 = outContent.toString();
		
		// cover all edgs but does not cover several edge-pairs, [2,4,5] for instance.
		String str4 = "b" + newline + "b" + newline + "a"+ newline + "zero" + newline;
		assertEquals(str4, str0 + str1 + str2 + str3);
	}
	// No test case can satisfy either edge-pair coverage or prime path coverage
}

class M {

	public static void main(String[] argv){
		M obj = new M();
		if (argv.length > 0){
			obj.m(argv[0], argv.length);
		}
	}
	
	public void m(String arg, int i) {
		int q = 1;
		A o = null;
		Impossible nothing = new Impossible();
		if (i == 0)
			q = 4;
		q++;
		switch (arg.length()) {
			case 0: q /= 2; break;
			case 1: o = new A(); new B(); q = 25; break;
			case 2: o = new A(); q = q * 100;
			default: o = new B(); break; 
		}
		if (arg.length() > 0) {
			o.m();
		} else {
			System.out.println("zero");
		}
		nothing.happened();
	}
}

class A {
	public void m() { 
		System.out.println("a");
	}
}

class B extends A {
	public void m() { 
		System.out.println("b");
	}
}

class Impossible{
	public void happened() {
		// "2b||!2b?", whatever the answer nothing happens here
	}
}
