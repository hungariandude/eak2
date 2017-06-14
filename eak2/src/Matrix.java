import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * EAK2 ZH 2017.05.24.
 * 
 * @author Istvánfi Zsolt F90FVN
 */
public class Matrix {

    protected static ThreadLocal<DecimalFormat> NUMBER_FORMAT = ThreadLocal
            .withInitial(() -> new DecimalFormat("0.00"));

    /**
     * Létrehoz egy n-edrendû egységmátrixot.
     */
    public static Matrix createIdentityMatrix(final int n) {
        BigDecimal[] data = new BigDecimal[n * n];

        for (int i = 0; i < data.length; ++i) {
            if (i % (n + 1) == 0) {
                data[i] = BigDecimal.ONE;
            } else {
                data[i] = BigDecimal.ZERO;
            }
        }

        return new Matrix(data, n);
    }

    /**
     * Létrehoz egy n x m-es, azonos elemeket tartalmazó mátrixot.
     * 
     * @param n
     *            A mátrix sorainak száma
     * @param m
     *            A mátrix oszlopainak száma
     * @param value
     *            Az azonos elem
     */
    public static Matrix createMatrixOfConstant(final int n, final int m, final double value) {
        BigDecimal[] data = new BigDecimal[n * m];
        Arrays.fill(data, BigDecimal.valueOf(value));

        return new Matrix(data, n);
    }

    /**
     * Létrehoz egy n x m-es, 0.0 és 1.0 közötti véletlenszámokkal feltöltött
     * mátrixot.
     * 
     * @param n
     *            A mátrix sorainak száma
     * @param m
     *            A mátrix oszlopainak száma
     */
    public static Matrix createRandomMatrix(final int n, final int m) {
        BigDecimal[] data = new BigDecimal[n * m];

        Random random = new Random();
        for (int i = 0; i < data.length; ++i) {
            data[i] = BigDecimal.valueOf(random.nextDouble());
        }

        return new Matrix(data, n);
    }

    /**
     * A mátrixszorzás algoritmusa. Nem szálbiztos, ezt kívülről kell
     * biztosítani.
     */
    protected static Matrix doMultiplyAlgorithm(final Matrix matrix1, final Matrix matrix2) {
        Matrix result = new Matrix(matrix1.n, matrix2.m);

        for (int i = 0; i < matrix1.n; ++i) {
            for (int k = 0; k < matrix1.m; ++k) {
                for (int j = 0; j < matrix2.m; ++j) {
                    BigDecimal value = result.getBigDecimalValueAt(i, j)
                            .add(matrix1.getBigDecimalValueAt(i, k).multiply(matrix2.getBigDecimalValueAt(k, j)));
                    result.setBigDecimalValueAt(i, j, value);
                }
            }
        }

        return result;
    }

    protected final BigDecimal[] data;
    protected final int n, m;

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();

    /**
     * Konstruktor.
     * 
     * @param data
     *            A mátrix sorfolytonos tömb reprezentációja
     * @param n
     *            A mátrix sorainak száma
     */
    protected Matrix(final BigDecimal[] data, final int n) {
        this.data = data;
        this.n = n;
        this.m = data.length / n;
    }

    /**
     * Konstruktor. Egy n x m-es, üres mátrixot hoz létre (csak 0-kat
     * tartalmaz).
     * 
     * @param n
     *            A mátrix sorainak száma
     * @param m
     *            A mátrix oszlopainak száma
     */
    public Matrix(final int n, final int m) {
        this.n = n;
        this.m = m;
        this.data = new BigDecimal[n * m];
        Arrays.fill(data, BigDecimal.ZERO);
    }

    public Matrix sequentialMultiply(final Matrix other) {
        if (m != other.n) {
            throw new IllegalArgumentException(
                    "The row count of other matrix must match the column count of this matrix.");
        }

        readLock.lock();
        other.readLock.lock();
        try {
            return doMultiplyAlgorithm(this, other);
        } finally {
            readLock.unlock();
            other.readLock.unlock();
        }
    }

    public Matrix parallelMultiply(final Matrix other, final int parallelism) {
        if (m != other.n) {
            throw new IllegalArgumentException(
                    "The row count of other matrix must match the column count of this matrix.");
        }
        if (parallelism < 1) {
            throw new IllegalArgumentException("Parameter parallelism must be bigger than zero.");
        }

        ForkJoinPool pool = new ForkJoinPool(parallelism);
        readLock.lock();
        other.readLock.lock();

        try {
            return pool.invoke(new DivideAndConquerTask(this, other, parallelism));
        } finally {
            readLock.unlock();
            other.readLock.unlock();
            pool.shutdown();
        }
    }

    /**
     * Mátrixösszeadás.
     */
    public Matrix add(final Matrix other) {
        readLock.lock();
        other.readLock.lock();

        BigDecimal[] newData = new BigDecimal[data.length];
        for (int i = 0; i < data.length; ++i) {
            newData[i] = data[i].add(other.data[i]);
        }

        readLock.unlock();
        other.readLock.unlock();

        return new Matrix(newData, n);
    }

    /**
     * Visszaadja a megadott pozíción lévõ értéket.
     * 
     * @param rowIndex
     *            A sorindex (0-tól indexelõdik)
     * @param colIndex
     *            Az oszlopindex (0-tól indexelõdik)
     */
    public double getElementAt(final int rowIndex, final int colIndex) {
        readLock.lock();
        try {
            return getBigDecimalValueAt(rowIndex, colIndex).doubleValue();
        } finally {
            readLock.unlock();
        }
    }

    protected BigDecimal getBigDecimalValueAt(final int rowIndex, final int colIndex) {
        return data[rowIndex * m + colIndex];
    }

    /**
     * Beállítja a megadott pozíción az értéket.
     * 
     * @param rowIndex
     *            A sorindex (0-tól indexelõdik)
     * @param colIndex
     *            Az oszlopindex (0-tól indexelõdik)
     * @param Az
     *            új érték az adott pozíción
     */
    public void setElementAt(final int rowIndex, final int colIndex, final double value) {
        writeLock.lock();
        setBigDecimalValueAt(rowIndex, colIndex, BigDecimal.valueOf(value));
        writeLock.unlock();
    }

    protected void setBigDecimalValueAt(final int rowIndex, final int colIndex, final BigDecimal value) {
        data[rowIndex * m + colIndex] = value;
    }

    /**
     * A mátrix sorainak száma.
     */
    public int getN() {
        return n;
    }

    /**
     * A mátrix oszlopainak száma.
     */
    public int getM() {
        return m;
    }

    @Override
    public String toString() {
        readLock.lock();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; ++i) {
            sb.append(NUMBER_FORMAT.get().format(data[i]));
            if ((i + 1) % m != 0) {
                sb.append(' ');
            } else if (i != data.length - 1) {
                sb.append(System.lineSeparator());
            }
        }
        readLock.unlock();

        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        readLock.lock();
        int dataHash = Arrays.hashCode(data);
        readLock.unlock();
        result = prime * result + dataHash;
        result = prime * result + n;
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Matrix other = (Matrix) obj;
        readLock.lock();
        other.readLock.lock();
        boolean dataEquals = Arrays.equals(data, other.data);
        readLock.unlock();
        other.readLock.unlock();
        if (!dataEquals)
            return false;
        if (n != other.n)
            return false;
        return true;
    }

    private static class DivideAndConquerTask extends RecursiveTask<Matrix> {

        private static final long serialVersionUID = 1L;

        private final int parallelism;
        private final Matrix matrix1, matrix2;

        public DivideAndConquerTask(final Matrix matrix1, final Matrix matrix2, final int parallelism) {
            this.matrix1 = matrix1;
            this.matrix2 = matrix2;
            this.parallelism = parallelism;
        }

        /**
         * Divide and conquer algorithm (non-square matrix variant)
         */
        @Override
        protected Matrix compute() {
            int max = Math.max(Math.max(matrix1.n, matrix1.m), matrix2.m);

            if (parallelism > 1 && max > 1) {
                int parallelism1 = parallelism / 2;
                int parallelism2 = parallelism - parallelism1;

                if (max == matrix1.n) {
                    Matrix[] split = splitHorizontally(matrix1);

                    DivideAndConquerTask task1 = new DivideAndConquerTask(split[0], matrix2, parallelism1);
                    DivideAndConquerTask task2 = new DivideAndConquerTask(split[1], matrix2, parallelism2);

                    task2.fork();
                    Matrix upper = task1.compute();
                    Matrix lower = task2.join();

                    return appendVertically(upper, lower);
                } else if (max == matrix2.m) {
                    Matrix[] split = splitVertically(matrix2);

                    DivideAndConquerTask task1 = new DivideAndConquerTask(matrix1, split[0], parallelism1);
                    DivideAndConquerTask task2 = new DivideAndConquerTask(matrix1, split[1], parallelism2);

                    task2.fork();
                    Matrix left = task1.compute();
                    Matrix right = task2.join();

                    return appendHorizontally(left, right);
                } else { // max == matrix1.m
                    Matrix[] split1 = splitVertically(matrix1);
                    Matrix[] split2 = splitHorizontally(matrix2);

                    DivideAndConquerTask task1 = new DivideAndConquerTask(split1[0], split2[0], parallelism1);
                    DivideAndConquerTask task2 = new DivideAndConquerTask(split1[1], split2[1], parallelism2);

                    task2.fork();
                    Matrix left = task1.compute();
                    Matrix right = task2.join();

                    return left.add(right);
                }
            } else {
                return doMultiplyAlgorithm(matrix1, matrix2);
            }
        }

        /**
         * Vízszintesen vágja félbe a mátrixot. Ha a sorok száma páratlan, az
         * alsó mátrix lesz a nagyobb.
         */
        private Matrix[] splitHorizontally(final Matrix matrix) {
            int n1 = matrix.n / 2;
            int n2 = matrix.n - n1;

            BigDecimal[] data1 = Arrays.copyOfRange(matrix.data, 0, n1 * matrix.m);
            BigDecimal[] data2 = Arrays.copyOfRange(matrix.data, n1 * matrix.m, matrix.n * matrix.m);
            Matrix newMatrix1 = new Matrix(data1, n1);
            Matrix newMatrix2 = new Matrix(data2, n2);

            return new Matrix[] { newMatrix1, newMatrix2 };
        }

        /**
         * Függőlegesen vágja félbe a mátrixot. Ha az oszlopok száma páratlan, a
         * jobb oldali mátrix lesz a nagyobb.
         */
        private Matrix[] splitVertically(final Matrix matrix) {
            int m1 = matrix.m / 2;
            int m2 = matrix.m - m1;

            BigDecimal[] data1 = new BigDecimal[matrix.n * m1];
            BigDecimal[] data2 = new BigDecimal[matrix.n * m2];
            for (int i = 0; i < matrix.n; ++i) {
                System.arraycopy(matrix.data, i * matrix.m, data1, i * m1, m1);
                System.arraycopy(matrix.data, i * matrix.m + m1, data2, i * m2, m2);
            }
            Matrix newMatrix1 = new Matrix(data1, matrix.n);
            Matrix newMatrix2 = new Matrix(data2, matrix.n);

            return new Matrix[] { newMatrix1, newMatrix2 };
        }

        /**
         * Hozzácsatol egy mátrix alá egy másik mátrixot.
         */
        private Matrix appendVertically(final Matrix upper, final Matrix lower) {
            int newN = upper.n + lower.n;
            BigDecimal[] newData = new BigDecimal[newN * upper.m];
            System.arraycopy(upper.data, 0, newData, 0, upper.data.length);
            System.arraycopy(lower.data, 0, newData, upper.n * upper.m, lower.data.length);
            return new Matrix(newData, newN);
        }

        /**
         * Hozzácsatol egy mátrix jobb oldalára egy másik mátrixot.
         */
        private Matrix appendHorizontally(final Matrix left, final Matrix right) {
            int newM = left.m + right.m;
            BigDecimal[] newData = new BigDecimal[left.n * newM];
            for (int i = 0; i < left.n; ++i) {
                System.arraycopy(left.data, i * left.m, newData, i * newM, left.m);
                System.arraycopy(right.data, i * right.m, newData, i * newM + left.m, right.m);
            }
            return new Matrix(newData, left.n);
        }
    }

}
