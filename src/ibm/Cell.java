package ibm;

import java.io.Serializable;
import java.util.ArrayList;

public class Cell implements Serializable {
	private static final long serialVersionUID = 1L;
	//
	public int type;
	public boolean filament;
	public Ball[] ballArray = 	new Ball[1];								// Note that this ballArray has the same name as CModel's
	public ArrayList<SpringRod> rodSpringArray = new ArrayList<SpringRod>(0);
	public ArrayList<Cell> stickCellArray = new ArrayList<Cell>(0);
	public ArrayList<SpringStick> stickSpringArray = new ArrayList<SpringStick>(0);
	public ArrayList<SpringAnchor> anchorSpringArray = new ArrayList<SpringAnchor>(0);
	public ArrayList<SpringFil> filSpringArray = new ArrayList<SpringFil>(0);
	public Cell mother;
	public int born;														// Growth iteration at which this cell was born
	// CFD stuff
	public double Rx = 0.0;													// Reaction rate for this cell, normalised to substrate [mol/s]
	// Pointer stuff
	public Model model;
	// Var. radius stuff
	public double radiusModifier;

	////////////////////////////////////////////////////////////////////////////////////////////
	
	public Cell(int type, double n, double radiusModifier, double base0x, double base0y, double base0z, double base1x, double base1y, double base1z, boolean filament, Model model) {
		this.model = model;
		this.type = type; 				// type == 0 || type == 1 is spherical cell. type == 2 || 3 is variable radius balls rod cell. type == 4 || 5 is fixed radius (variable length) rod cell
		this.filament = filament;
		this.radiusModifier = radiusModifier;
		this.born = model.growthIter;
		
		model.cellArray.add(this);				// Add it here so we can use cell.Index()
		
		if(type<2) { // Leaves ballArray and springArray, and mother
			ballArray[0] = new Ball(base0x, base0y, base0z, n,   0, this);
		} else if(type<6){
			ballArray = 	new Ball[2];		// Reinitialise ballArray to contain 2 balls
			new Ball(base0x, base0y, base0z, n/2.0, 0, this);		// Constructor adds it to the array
			new Ball(base1x, base1y, base1z, n/2.0, 1, this);		// Constructor adds it to the array
			new SpringRod(ballArray[0],ballArray[1]);				// Constructor adds it to the array
		} else {
			throw new IndexOutOfBoundsException("Cell type: " + type);
		}
	}
	
	// Vector3d instead of double
	public Cell(int type, double n, double radiusModifier, Vector3d base0, Vector3d base1, boolean filament, Model model) {
		this(type, n, radiusModifier, base0.x, base0.y, base0.z, base1.x, base1.y, base1.z, filament, model);
	}
	
	// Without radiusModifier (generate randomly or skip, depending on model.radiusModifier)
	public Cell(int type, double n, double base0x, double base0y, double base0z, double base1x, double base1y, double base1z, boolean filament, Model model) {
		this(type, n, 
				model.radiusCellStDev[type]==0.0?0.0:(model.radiusCellStDev[type]*random.rand.Gaussian()),		// Assign radius modifier due to deviation. If no modifier skip this, maintains reproducibility (WORKAROUND). Has to be done inline due Java limitations
				base0x, base0y, base0z, base1x, base1y, base1z, filament, model);
	}
	
	public Cell(int type, double n, Vector3d base0, Vector3d base1, boolean filament, Model model) {
		this(type, n,
				model.radiusCellStDev[type]==0.0?0.0:(model.radiusCellStDev[type]*random.rand.Gaussian()),		// Assign radius modifier due to deviation. If no modifier skip this, maintains reproducibility (WORKAROUND). Has to be done inline due Java limitations 
				base0.x, base0.y, base0.z, base1.x, base1.y, base1.z, filament, model);
	}
	
	
	/////////////////////////////////////////////////////////
	
	public int Index() {
		ArrayList<Cell> array = model.cellArray;
		for(int index=0; index<array.size(); index++) {
			if(array.get(index).equals(this))	return index;
		}
		return -1;
	}
	
	public int Anchor() {
		for(Ball ball : ballArray) {
			Vector3d substratumPos = new Vector3d(ball.pos);
			substratumPos.y = 0.0;
			new SpringAnchor(ball, substratumPos);
		}

		// Add sibling springs, assuming all anchors in this cell are siblings
		for(int ii=0; ii<anchorSpringArray.size(); ii++) {
			for(int jj=ii+1; jj<anchorSpringArray.size(); jj++) {
				SpringAnchor anchor0 = anchorSpringArray.get(ii);
				SpringAnchor anchor1 = anchorSpringArray.get(jj);
				anchor0.siblingArray.add(anchor1);
				anchor1.siblingArray.add(anchor0);
			}
		}
		return anchorSpringArray.size();
	}
	

	public boolean IsFilament(Cell cell) {
		if(!this.filament && !cell.filament)	return false;
		
		if((mother!=null && mother.equals(cell)) || (cell.mother!=null && cell.mother.equals(this))) 		return true;
		else return false;
	}
	
	public int Stick(Cell cell) {
		// Determine how many sticking springs we need
		int NSpring0 = 0, NSpring1 = 0;
		if(type<2) 			NSpring0 = 1; else 
		if(type<6)			NSpring0 = 2; else
			throw new IndexOutOfBoundsException("Cell type: " + type);
		if(cell.type<2) 	NSpring1 = 1; else 
		if(cell.type<6) 	NSpring1 = 2; else
			throw new IndexOutOfBoundsException("Cell type: " + cell.type);
		
		int NSpring = NSpring0 * NSpring1;
		Cell cell0, cell1;
		if(type > 1 && cell.type < 2) {		// Sphere goes first (see indexing next paragraph)
			cell0 = cell;
			cell1 = this;
		} else {							// Sphere goes first. Other cases, it doesn't matter
			cell0 = this;
			cell1 = cell;
		}
		
		SpringStick[] stickArray = new SpringStick[NSpring];
		for(int iSpring = 0; iSpring < NSpring; iSpring++) {					// Create all springs, including siblings, with input balls
			Ball ball0 = cell0.ballArray[iSpring/2];							// 0, 0, 1, 1, ...
			Ball ball1 = cell1.ballArray[iSpring%2];							// 0, 1, 0, 1, ...
			SpringStick spring = new SpringStick(	ball0, ball1);
			stickArray[iSpring] = spring;
		}
		
		// Define siblings, link them OPTIMISE
		for(int iSpring = 0; iSpring < NSpring; iSpring++) {				// For each spring and sibling spring			
			SpringStick spring = stickArray[iSpring];			
			for(int iSpring2 = 0; iSpring2 < NSpring; iSpring2++) {			
				if(iSpring != iSpring2) {									// For all its siblings
					spring.siblingArray.add(stickArray[iSpring2]);
				}
			}
		}
		// Tell cells they're stuck to each other
		this.stickCellArray.add(cell);
		cell.stickCellArray.add(this);
		
		return NSpring;
	}
			
	public double GetAmount() {
		double amount = 0;
		for(Ball ball : ballArray) {
			amount += ball.n;
		}
		return amount;
	}
	
	public void SetAmount(double newAmount) {
		if(type<2) {
			ballArray[0].n = newAmount;
			ballArray[0].radius = ballArray[0].Radius();
		} else if(type<6){
			ballArray[0].n = newAmount/2.0;
			ballArray[0].radius = ballArray[0].Radius();
			ballArray[1].n = newAmount/2.0;
			ballArray[1].radius = ballArray[1].Radius();
			// Reset rod spring length
			for(Spring rod : ballArray[0].cell.rodSpringArray) rod.ResetRestLength();
		} else {
			throw new IndexOutOfBoundsException("Cell type: " + type);
		}
	}
	
	public double SurfaceArea() {
		return SurfaceArea(1.0);
	}
	
	public double SurfaceArea(double scale) {
		if(type<2) {
			return 4*Math.PI * Math.pow(ballArray[0].radius*scale, 2);
		} else if(type<6) {	// Assuming radii are equal
			double Aballs = 4.0*Math.PI * Math.pow(ballArray[0].radius*scale, 2); 	// Two half balls
			double height = rodSpringArray.get(0).restLength*scale;					// height == distance between balls
			double Acyl = 	2.0*Math.PI * ballArray[0].radius*scale * height;		// area of wall of cylinder
			return Aballs + Acyl;
		} else {
			throw new IndexOutOfBoundsException("Cell type: " + type);
		}
	}
	
	public double Volume() {
		return Volume(1.0);
	}
	
	public double Volume(double scale) {
		if(type<2) {
			return 4.0/3.0*Math.PI*Math.pow(ballArray[0].radius*scale, 3); 
		} else if(type<6) {
			return 4.0/3.0*Math.PI*Math.pow(ballArray[0].radius*scale, 3)  +  Math.PI*Math.pow(ballArray[0].radius*scale, 2)*rodSpringArray.get(0).restLength*scale;
		} else {
			throw new IndexOutOfBoundsException("Cell type: " + type);
		}
	}
	
	public double GetDistance(Cell cell) {										// This method probably has a higher overhead than the code in CollisionDetection
		if(this.type<2) {														// Sphere-???
			if(cell.type<2)	{													// Sphere-sphere
				return cell.ballArray[0].pos.minus(ballArray[0].pos).norm();
			} else if(cell.type<6) {											// Sphere-rod
				ericson.ReturnObject C = ericson.DetectCollision.LinesegPoint(cell.ballArray[0].pos, cell.ballArray[1].pos, this.ballArray[0].pos);
				return C.dist;
			} else {															// Unknown!
				throw new IndexOutOfBoundsException("Cell type: " + cell.type);
			}
		} else if(this.type<6) {												// Rod-???
			if(cell.type<2) {													// Rod-sphere
				ericson.ReturnObject C = ericson.DetectCollision.LinesegPoint(this.ballArray[0].pos, this.ballArray[1].pos, cell.ballArray[0].pos);
				return C.dist; 
			} else if(cell.type<6) {											// Rod-rod
				ericson.ReturnObject C = ericson.DetectCollision.LinesegLineseg(this.ballArray[0].pos, this.ballArray[1].pos, cell.ballArray[0].pos, cell.ballArray[1].pos);
				return C.dist;		
			} else {															// Unknown!
				throw new IndexOutOfBoundsException("Cell type: " + cell.type);
			}
		} else {
			throw new IndexOutOfBoundsException("Cell type: " + this.type);
		}
	}
	
	public Cell GetNeighbour() {										// Returns neighbour of cell in straight filament. Not of branched
		for(SpringFil fil : filSpringArray) {
			if(fil.type==4) {											// Get the other cell in the straight filament, via short spring
				if(fil.ballArray[0] == ballArray[1])		return fil.ballArray[1].cell;		// We only look at ball1, so we're already excluding mother (that is connected at ball0)
				if(fil.ballArray[1] == ballArray[1])		return fil.ballArray[0].cell; 
			}
		}
		// Nothing found
		return null;
	}
}

