import java.util.concurrent.ExecutionException;

public class Main {

    public static void main(final String... args) throws InterruptedException, ExecutionException {
        // parancssori paraméter beolvasása
        int n = 0;
        try {
            n = Integer.parseInt(args[0]);
        } catch (IndexOutOfBoundsException ex) {
            System.err.println("No argument");
            System.exit(1);
        } catch (NumberFormatException ex) {
            System.err.println("Argument is not a positive integer");
            System.exit(1);
        }

        // 1. feladat
        Matrix identityMatrix3 = Matrix.createIdentityMatrix(3);
        Matrix matrixOfOnes34 = Matrix.createMatrixOfConstant(3, 4, 1.0);
        System.out.println(identityMatrix3.sequentialMultiply(matrixOfOnes34).toString());

        System.out.println();

        // 2. feladat
        Matrix matrixOfOnes43 = Matrix.createMatrixOfConstant(4, 3, 1.0);
        System.out.println(matrixOfOnes34.sequentialMultiply(matrixOfOnes43).toString());

        System.out.println();

        // 3. feladat
        Matrix randomMatrix31 = Matrix.createRandomMatrix(3, 3);
        Matrix randomMatrix32 = Matrix.createRandomMatrix(3, 3);
        Matrix seqResMatrix = randomMatrix31.sequentialMultiply(randomMatrix32);
        Matrix parResMatrix = randomMatrix31.parallelMultiply(randomMatrix32,
                Runtime.getRuntime().availableProcessors());
        System.out.println(seqResMatrix.equals(parResMatrix));

        System.out.println();

        // 4. feladat
        System.out.print("seq:    ");
        for (int i = 0; i < 3; ++i) {
            Matrix randomMatrixN1 = Matrix.createRandomMatrix(n, n);
            Matrix randomMatrixN2 = Matrix.createRandomMatrix(n, n);
            long start = System.currentTimeMillis();
            randomMatrixN1.sequentialMultiply(randomMatrixN2);
            long end = System.currentTimeMillis();

            System.out.print((end - start) + " ");
        }

        System.out.println();

        // 5. feladat
        for (int i = 1; i <= Runtime.getRuntime().availableProcessors(); ++i) {
            System.out.print("par " + i + ":  ");
            for (int j = 0; j < 3; ++j) {
                Matrix randomMatrixN1 = Matrix.createRandomMatrix(n, n);
                Matrix randomMatrixN2 = Matrix.createRandomMatrix(n, n);
                long start = System.currentTimeMillis();
                randomMatrixN1.parallelMultiply(randomMatrixN2, i);
                long end = System.currentTimeMillis();

                System.out.print((end - start) + " ");
            }
            System.out.println();
        }
    }

}
