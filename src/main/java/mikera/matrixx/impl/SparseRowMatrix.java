package mikera.matrixx.impl;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.HashSet;
// import java.util.Map;
// import java.util.Map.Entry;




import mikera.arrayz.INDArray;
import mikera.arrayz.ISparse;
import mikera.indexz.Index;
import mikera.matrixx.AMatrix;
import mikera.matrixx.Matrixx;
import mikera.vectorz.AVector;
import mikera.vectorz.Vector;
import mikera.vectorz.Vectorz;
import mikera.vectorz.impl.SingleElementVector;
import mikera.vectorz.impl.SparseIndexedVector;
import mikera.vectorz.util.ErrorMessages;
import mikera.vectorz.util.VectorzException;

/**
 * Matrix stored as a collection of normally sparse row vectors.
 * 
 * This format is especially efficient for:
 * - innerProduct() with another matrix, especially one with efficient
 *   column access like SparseColumnMatrix
 * - access via getRow() operation
 * - transpose into SparseColumnMatrix
 * 
 * @author Mike
 * 
 */
public class SparseRowMatrix extends ASparseRCMatrix implements ISparse, IFastRows {
	private static final long serialVersionUID = 8646257152425415773L;

	private static final long SPARSE_ELEMENT_THRESHOLD = 1000L;
	
	private final AVector emptyRow;

	protected SparseRowMatrix(int rowCount, int columnCount) {
		this(new AVector[rowCount],rowCount,columnCount);
	}

	protected SparseRowMatrix(AVector[] data, int rowCount, int columnCount) {
		super(rowCount,columnCount,data);
        if (data.length != rowCount)
            throw new IllegalArgumentException(ErrorMessages.incompatibleShapes(rowCount, data.length));
		emptyRow=Vectorz.createZeroVector(columnCount);
	}

	protected SparseRowMatrix(AVector... vectors) {
		this(vectors, vectors.length, vectors[0].length());
	}

	protected SparseRowMatrix(List<AVector> data, int rowCount, int columnCount) {
		this(data.toArray(new AVector[0]),rowCount,columnCount);
	}

	protected SparseRowMatrix(List<AVector> data) {
		this(data.toArray(new AVector[0]));
	}

//  	protected SparseRowMatrix(HashMap<Integer,AVector> data, int rowCount, int columnCount) {
//  		super(rowCount,columnCount,data);
//  		emptyColumn=Vectorz.createZeroVector(rowCount);
//  	}

	public static SparseRowMatrix create(int rows, int cols) {
		return new SparseRowMatrix(rows, cols);
	}

	public static SparseRowMatrix create(AVector[] data, int rows, int cols) {
		return new SparseRowMatrix(data, rows, cols);
	}

	public static SparseRowMatrix create(AVector... vecs) {
		return new SparseRowMatrix(vecs);
        // don't validate; user can call validate() if they want it.
	}
	
	public static SparseRowMatrix create(List<AVector> rows) {
		return create(rows.toArray(new AVector[rows.size()]));
	}
	
	public static INDArray create(ArrayList<INDArray> slices, int rows, int cols) {
		AVector[] vecs=new AVector[rows];
		for (int i=0; i<rows; i++) {
			INDArray a=slices.get(i);
			if ((a.dimensionality()!=1)||(a.sliceCount()!=cols)) throw new IllegalArgumentException(ErrorMessages.incompatibleShape(a));
			vecs[i]=a.asVector();
		}
		return wrap(vecs,rows,cols);
	}
	
	public static SparseRowMatrix wrap(AVector[] vecs, int rows, int cols) {
		return create(vecs, rows, cols);
	}
	
	public static SparseRowMatrix wrap(AVector... vecs) {
		return create(vecs);
	}
	
	public static SparseRowMatrix create(AMatrix source) {
		if (source instanceof SparseColumnMatrix) return ((SparseColumnMatrix)source).toSparseRowMatrix();
		int rc = source.rowCount();
		int cc = source.columnCount();
		AVector[] data = new AVector[rc];
		List<AVector> rows=source.getRows();
		for (int i = 0; i < rc; i++) {
			AVector row = rows.get(i);
			if (!row.isZero()) {
			    data[i] = Vectorz.createSparse(row);
			}
		}
		return SparseRowMatrix.wrap(data,rc,cc);
	}

	public static SparseRowMatrix wrap(List<AVector> vecs) {
		return create(vecs);
	}
	
//	public static SparseRowMatrix wrap(HashMap<Integer, AVector> data, int rows, int cols) {
//		return new SparseRowMatrix(data, rows, cols);
//	}

	@Override
	public int componentCount() {
		return rows;
	}
	
	@Override
	public AVector getComponent(int k) {
		AVector v=data[k];
		if (v==null) return emptyRow;
		return v;
	}

	@Override
	protected int lineLength() {
		return cols;
	}

	@Override
	public double get(int i, int j) {
		return getRow(i).get(j);
	}

	@Override
	public void set(int i, int j, double value) {
		checkIndex(i,j);
		unsafeSet(i,j,value);
	}

	@Override
	public double unsafeGet(int row, int column) {
		return getRow(row).unsafeGet(column);
	}

	@Override
	public void unsafeSet(int i, int j, double value) {
		AVector v = unsafeGetVector(i);
		if (v == null) {
			if (value == 0.0)
				return;
			v = SingleElementVector.create(value, j, cols);
		} else if (v.isFullyMutable()) {
			v.set(j, value);
			return;
		} else {
			v = v.sparseClone();
			v.unsafeSet(j, value);
		}
		unsafeSetVec(i, v);
	}
	
	@Override
	public void set(AMatrix a) {
		checkSameShape(a);
		List<AVector> srows=a.getRows();
		for (int i=0; i<rows; i++) {
			setRow(i,srows.get(i));
		}
	}
	
	@Override
	public void setRow(int i, AVector v) {
		data[i]=v.copy();
	}
	
	@Override
	public void addAt(int i, int j, double d) {
		if (d==0.0) return;
		AVector v=unsafeGetVector(i);
		if (v.isFullyMutable()) {
			v.addAt(j, d);
		} else {
			v=v.mutable();
			v.addAt(j, d);
			replaceRow(i,v);
		}
	}
	
	@Override
	public void addToArray(double[] targetData, int offset) {
        for (int i = 0; i < rows; ++i) {
			AVector v = unsafeGetVector(i);
			if (v != null) v.addToArray(targetData, offset+cols*i);
		}
	}

	private AVector ensureMutableRow(int i) {
		AVector v = unsafeGetVector(i);
		if (v == null) {
			AVector nv=SparseIndexedVector.createLength(cols);
            unsafeSetVec(i, nv);
			return nv;
		}
		if (v.isFullyMutable()) return v;
		AVector mv=v.mutable();
		unsafeSetVec(i, mv);
		return mv;
	}

    @Override
    public List<AVector> getColumns() {
        return toSparseColumnMatrix().getColumns();
    }
    
    public SparseColumnMatrix toSparseColumnMatrix() {
        SparseColumnMatrix cm=SparseColumnMatrix.create(rows,cols);
        for (int i = 0; i < rows; i++) {
            AVector rowVec = unsafeGetVector(i);
            if (null != rowVec) {
                Index nonSparseCols = rowVec.nonSparseIndex();
                int n=nonSparseCols.length();
                for (int k = 0; k < n; k++) {
                    int j = nonSparseCols.unsafeGet(k);
                    double v=rowVec.unsafeGet(j);
                    if (v!=0.0) {
                    	cm.unsafeSet(i,j, v);
                    }
                }
            }
        }
        return cm;    	
    }
    
	@Override
	public AVector getRow(int i) {
		AVector v = unsafeGetVector(i);
		if (v == null) return emptyRow;
		return v;
	}

    @Override
	public List<AVector> getRows() {
        ArrayList<AVector> rowList = new ArrayList<AVector>(rows);
		for (int i = 0; i < rows; i++) {
            AVector v = unsafeGetVector(i);
            if (v == null) v=emptyRow;
            rowList.add(v);
        }
		return rowList;
	}
	
	@Override
	public AVector getRowView(int i) {
		return ensureMutableRow(i);
	}
	
	@Override
	public boolean isUpperTriangular() {
		int rc=rowCount();
		for (int i=1; i<rc; i++) {
			if (!getRow(i).isRangeZero(0, i)) return false;
		}
		return true;
	}

	@Override
	public void swapRows(int i, int j) {
		if (i == j)
			return;
		AVector a = unsafeGetVector(i);
		AVector b = unsafeGetVector(j);
		unsafeSetVec(i, b);
		unsafeSetVec(j, a);
	}

	@Override
	public void replaceRow(int i, AVector vec) {
		if (vec.length() != cols)
			throw new IllegalArgumentException(ErrorMessages.incompatibleShape(vec));
        unsafeSetVec(i, vec);
	}

	@Override
	public void add(AMatrix a) {
		checkSameShape(a);
		int rc=rowCount();
		for (int i=0; i<rc; i++) {
			AVector myVec=unsafeGetVector(i);
			AVector aVec=a.getRow(i);
			if (myVec==null) {
				if (!aVec.isZero()) {
					unsafeSetVec(i,aVec.copy());
				}
			} else if (myVec.isFullyMutable()) {
				myVec.add(aVec);
			} else {
				unsafeSetVec(i,myVec.addCopy(aVec));
			}
		}
	}
	
	@Override
	public void copyRowTo(int i, double[] data, int offset) {
		AVector v=this.unsafeGetVector(i);
		if (v==null) {
			Arrays.fill(data, offset, offset+cols, 0.0);			
		} else {
			v.getElements(data, offset);
		}
	}
	
	@Override
	public void copyColumnTo(int col, double[] targetData, int offset) {
        for (int i = 0; i < rows; ++i) {
            AVector v = unsafeGetVector(i);
            targetData[offset+i] = (v==null) ? 0.0 : v.unsafeGet(col);
		}		
	}

	@Override
	public SparseColumnMatrix getTransposeView() {
		return SparseColumnMatrix.wrap(data, cols, rows);
	}

	@Override
	public AMatrix multiplyCopy(double a) {
		long n=componentCount();
		AVector[] ndata=new AVector[(int)n];
		for (int i = 0; i < n; ++i) {
            AVector v = unsafeGetVector(i);
            if (v != null)
                ndata[i] = v.multiplyCopy(a);
		}
		return wrap(ndata,rows,cols);
	}
	
	@Override
	public void multiplyRow(int i, double value) {
		if (value==0.0) {
			unsafeSetVec(i,null);
			return;
		}
		AVector v = unsafeGetVector(i);
		if (v==null) return;
		v=v.multiplyCopy(value);
		unsafeSetVec(i,v);
	}

	@Override
	public AVector innerProduct(AVector a) {
		return transform(a);
	}
	
	@Override
	public AVector transform(AVector a) {
		AVector r=Vector.createLength(rows);
		for (int i=0; i<rows; i++) {
			r.set(i,getRow(i).dotProduct(a));
		}
		return r;
	}

	@Override
	public double[] toDoubleArray() {
		double[] ds=new double[rows*cols];
		// we use adding to array, since rows themselves are likely to be sparse
        for (int i = 0; i < rows; ++i) {
            AVector v = unsafeGetVector(i);
			if (v != null)
                v.addToArray(ds, i*cols);
		}
		return ds;
	}
	
	@Override
	public AMatrix innerProduct(AMatrix a) {
		if (a instanceof SparseColumnMatrix) {
			return innerProduct((SparseColumnMatrix) a);
		}
		SparseRowMatrix r = Matrixx.createSparse(rows, a.columnCount());

        for (int i = 0; i < rows; ++i) {
			AVector row = unsafeGetVector(i);
            if (! ((row == null) || (row.isZero()))) {
			    r.replaceRow(i,row.innerProduct(a));
            }
		}
		return r;
	}
	
	/**
	 * Specialised inner product for sparse row matrix multiplied by sparse column matrix. 
	 *  
	 * @param a
	 * @return
	 */
	public SparseRowMatrix innerProduct(SparseColumnMatrix a) {
		return innerProduct(SparseRowMatrix.create(a));
	}
	
	public SparseRowMatrix innerProduct(SparseRowMatrix a) {
		SparseRowMatrix r = Matrixx.createSparse(rows, a.columnCount());
		for (int i = 0; i < rows; ++i) {
			AVector row = unsafeGetVector(i);
            if (row != null) {
			    r.replaceRow(i,row.innerProduct(a));
            }
		}
		return r;
	}


	@Override
	public SparseRowMatrix exactClone() {
		SparseRowMatrix result = new SparseRowMatrix(rows, cols);
        for (int i = 0; i < rows; ++i) {
			AVector row = unsafeGetVector(i);
			if (row != null)
                result.replaceRow(i, row.exactClone());
		}
		return result;
	}
	
	@Override
	public AMatrix clone() {
		if (this.elementCount() < SPARSE_ELEMENT_THRESHOLD)
			return super.clone();
		return exactClone();
	}

	@Override
	public AMatrix sparse() {
		return this;
	}

	@Override
	public void validate() {
		super.validate();
		for (int i=0; i<rows; i++) {
			if (getRow(i).length()!=cols) throw new VectorzException("Invalid column count at row: "+i);
		}
	}
	
	@Override
	public boolean equals(AMatrix m) {
		if (m==this) return true;
		if (!isSameShape(m)) return false;
		for (int i=0; i<rows; i++) {
			AVector v=unsafeGetVector(i);
            AVector ov = m.getRow(i);
			if (v==null) {
				if (!ov.isZero()) return false;
			} else {
				if (!v.equals(ov)) return false;
			}
		}
		return true;
	}

	public static AVector innerProduct(AMatrix a, AVector b) {
		// TODO: consider reducing working set?
		return create(a).innerProduct(b);
	}

	public static AMatrix innerProduct(AMatrix a, AMatrix b) {
		// TODO: consider reducing working set?
		return create(a).innerProduct(b);
	}


}
