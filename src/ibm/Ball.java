package ibm;

import java.io.Serializable;
import java.util.ArrayList;


public class Ball implements Serializable {
	private static final long serialVersionUID = 1L;
	//
	public double n;		// [mol] Chemical amount 
	public double radius;
	public Vector3d pos;
	public Vector3d vel;
	public Vector3d force;
//	public int index;
	public Cell cell;
//	public int ballArrayIndex;
	
	///////////////////////////////////////////////////////////////////
	
	public Ball(double posx, double posy, double posz, double amount, int ballArrayIndex, Cell cell){	// Create a new ball, make it belong to cell
		this.cell = cell;
		Model model = cell.model; 
		
		pos = new Vector3d(posx, posy, posz);
		vel = new Vector3d(0, 0, 0);
		force = new Vector3d(0, 0, 0);

		this.n = amount;
		
		// Add ball to required arrays
//		this.ballArrayIndex = ballArrayIndex;
		cell.ballArray[ballArrayIndex] = this;
		model.ballArray.add(this);
		// Update the radius
		this.radius = Radius();
	}
		
	public Ball() {} 				// Empty constructor for loading. Doesn't add the ball to any ball arrays!
	
	/////////////////////////////////////////////////////
	
	public double Radius() {		
		return Radius(n, cell.type, cell.model, cell.radiusModifier);
	}
	
	public static double Radius(double n, int type, Model model) {
		return Radius(n, type, model, 0.0);
	}
	
	public static double Radius(double n, int type, Model model, double radiusModifier) {
		if (type<2) {
			return Math.pow( 							n*model.MWX / (Math.PI * model.rhoX * 4.0/3.0), .333333);						// Note that rho is in kg m-3 but cell mass is in Cmol
		} else {
			double aspect = model.lengthCellMax[type] / model.radiusCellMax[type];														// Aspect is here length over radius (not diameter) 
			if(type<4) {			// type == 2 || 3 is variable radius balls
				return Math.pow(					2.0*n*model.MWX / (Math.PI * model.rhoX * (aspect + 4.0/3.0)), .333333);			// Note that 2.0*mass could at some point in the future be wrong. Can't use GetMass() yet
			} else {					// type == 4 || 5 is fixed radius (variable length) rod
				return Math.pow(     model.nCellMax[type]*model.MWX	/ (Math.PI * model.rhoX * (aspect + 4.0/3.0)), .333333) + radiusModifier;			// No longer static due to radiusModifier
			}
		}
	}
	
	public int Index() {
		Model model = cell.model;
		ArrayList<Ball> array = model.ballArray;
		for(int index=0; index<array.size(); index++) {
			if(array.get(index).equals(this))	return index;
		}
		return -1;			// Error
	}
}
