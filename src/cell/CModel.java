package cell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.zip.GZIPOutputStream;

import backbone.Assistant;
import random.rand;
import NR.*;

public class CModel implements Serializable {
	// Set serializable information
	private static final long serialVersionUID = 1L;
	// Model properties
	public String name = "default";
	public int randomSeed = 5;					// Makes first 3 rods, then 3 spheres (I got lucky)
	public boolean sticking = true;
	public boolean anchoring = false;
	public boolean filament = false;
	public boolean gravity = false;
	public boolean gravityZ = false;
	// Spring constants
	public double Kc 	= 2e7;					// collision (per ball)
	public double Kw 	= 1e7;					// wall spring (per ball)
	public double Kr 	= 2.5e5;				// internal cell spring (per ball)
	public double Kf 	= 1e6;					// filament spring (per ball average)
	public double Kan	= 5e4;					// anchor (per ball)
	public double Ks 	= 5e4;					// sticking (per ball average)
	public double[] stretchLimAnchor = {0.6, 1.4};// Maximum tension and compression (1-this value) for anchoring springs
	public double formLimAnchor = 1.1;			// Multiplication factor for rest length to form anchors. Note that actual rest length is the distance between the two, which could be less
	public double[] stretchLimStick = {0.6, 1.4};// Maximum tension and compression (1-this value) for sticking springs
	public double formLimStick = 1.1; 			// Multiplication factor for rest length to form sticking springs. 
	public double[] stretchLimFil = {0.4, 1.6};	// Maximum tension and compression (1-this value) for sticking springs
	// Domain properties
	public double Kd 	= 2.5e3;				// drag force coefficient (per BALL)
	public double G		= -9.8;					// [m/s2], acceleration due to gravity
	public double rhoWater = 1000;				// [kg/m3], density of bulk liquid (water)
	public double rhoX	= 1010;					// [kg/m3], diatoma density
	public double MWX 	= 24.6e-3;				// [kg/mol], composition CH1.8O0.5N0.2
	public Vector3d L 	= new Vector3d(20e-6, 20e-6, 20e-6);	// [m], Dimensions of domain
	// Model biomass properties
	public int NXComp = 6;						// Types of biomass
	public int NdComp = 5;						// d for dynamic compound (e.g. total Ac)
	public int NcComp = 8;						// c for concentration (or virtual compound, e.g. Ac-)
	public int NAcidDiss = 4; 					// Number of acid dissociation reactions
	public int NInitCell = 6;					// Initial number of cells
	public int[] cellType = {1, 5};				// Cell types used by default
	public double[] aspect	= {0.0, 0.0, 4.0, 2.0, 5.0, 3.0};	// Aspect ratio of cells (last 2: around 4.0 and 2.0 resp.)
	// Ball properties
	public double[] nCellInit = {2.42e-19*rhoX, 1.55e-17*rhoX, 1.70e-18*rhoX, 2.62e-17*rhoX, 1.70e-18*rhoX, 2.62e-17*rhoX};		// [Cmol] initial cell, when created at t=0. Factor *0.9 used for initial mass type<4
	public double[] nBallInit = {nCellInit[0], nCellInit[1], nCellInit[2]/2.0, nCellInit[3]/2.0, nCellInit[4]/2.0, nCellInit[5]/2.0};				// [Cmol] initial mass of one ball in the cell
	public double[] nCellMax = {nCellInit[0]*2.0, nCellInit[1]*2.0, nCellInit[2]*2.0, nCellInit[3]*2.0, nCellInit[4]*2.0, nCellInit[5]*2.0};		// [Cmol] max mass of cells before division;
	// Progress
	public double growthTime = 0.0;				// [s] Current time for the growth
	public double growthTimeStep = 3600.0;		// [s] Time step for growth
	public int growthIter = 0;					// [-] Counter time iterations for growth
	public double movementTime = 0.0;			// [s] initial time for movement (for ODE solver)
	public double movementTimeStep = 2e-2;		// [s] output time step  for movement
	public double movementTimeStepEnd = 10e-2;	// [s] time interval for movement (for ODE solver), 5*movementTimeStep by default
	public int movementIter = 0;				// [-] counter time iterations for movement
	// Arrays
	public ArrayList<CCell> cellArray = new ArrayList<CCell>(NInitCell);
	public ArrayList<CBall> ballArray = new ArrayList<CBall>(2*NInitCell);
	public ArrayList<CSpring> rodSpringArray = new ArrayList<CSpring>(NInitCell);
	public ArrayList<CSpring> stickSpringArray = new ArrayList<CSpring>(NInitCell);
	public ArrayList<CSpring> filSpringArray = new ArrayList<CSpring>(NInitCell);
	public ArrayList<CAnchorSpring> anchorSpringArray = new ArrayList<CAnchorSpring>(NInitCell);
	// === COMSOL STUFF ===
	// Biomass, assuming Cmol and composition CH1.8O0.5N0.2 (i.e. MW = 24.6 g/mol)
	//							type 0					type 1					type 2					type 3					type 4					type 5
	// 							m. hungatei				m. hungatei				s. fumaroxidans			s. fumaroxidans			s. fumaroxidans			s. fumaroxidans
	public double[] SMX = {		7.6e-3/MWX,				7.6e-3/MWX,				2.6e-3/MWX,				2.6e-3/MWX,				2.6e-3/MWX,				2.6e-3/MWX};				// [Cmol X/mol reacted] Biomass yields per flux reaction. All types from Scholten 2000, grown in coculture on propionate
	public double[] K = {		1e-21, 					1e-21, 					1e-5, 					1e-5, 					1e-5, 					1e-5};						//
	public double[] qMax = {	0.05/(SMX[0]*86400), 	0.05/(SMX[0]*86400), 	0.204*MWX*1e3/86400,	0.204*MWX*1e3/86400,	0.204*MWX*1e3/86400,	0.204*MWX*1e3/86400};		// [mol (Cmol*s)-1] M.h. from Robinson 1984, assuming yield, growth on NaAc in coculture. S.f. from Scholten 2000;
	public String[] rateEquation = {
			Double.toString(qMax[0]) + "*(c3*d3^4)/(K0+c3*d3^4)",		// type==0
			Double.toString(qMax[1]) + "*(c3*d3^4)/(K1+c3*d3^4)",		// type==1
			Double.toString(qMax[2]) + "*c2/(K2+c2)",					// type==2
			Double.toString(qMax[3]) + "*c2/(K3+c2)",					// type==3
			Double.toString(qMax[4]) + "*c2/(K4+c2)",					// type==4
			Double.toString(qMax[5]) + "*c2/(K5+c2)"};					// type==5
			
	// 	 pH calculations
	//							HPro		CO2			HCO3-		HAc
	//							0,			1,			2,			3
	public double[] Ka = {		1.34e-5,	4.6e-7,		4.69e-11, 	1.61e-5};								// From Wikipedia 120811. CO2 and H2CO3 --> HCO3- + H+;
	public String[] pHEquation = {																			// pH calculations
			"c2+c4+2*c5+c7-c0",											// Has -2 charge 
			"c2*c0/Ka0-c1", 
			"d0-c1-c2", 
			"c4*c0/Ka1-c3", 
			"c5*c0/Ka2-c4", 
			"d1-c3-c4-c5", 
			"c7*c0/Ka3-c6", 
			"d2-c6-c7"}; 	

	// Diffusion
	// 							ProT, 		CO2T,				AcT,				H2, 				CH4
	//							0,    		1,   				2, 					3,   				4
 	public double[] BCConc = new double[]{
 								1.0,		0.0, 				0.0,				0.0,				0.0	};			// [mol m-3]. equivalent to 1 [kg HPro m-3], neglecting Pro concentration
	public double[] D = new double[]{	
								1.060e-9,	1.92e-9,			1.21e-9,			4.500e-9,			1.88e-9};		// [m2 s-1]. Diffusion mass transfer Cussler 2nd edition. Methane through Witherspoon 1965
	public double[][] SMdiffusion = {
							{	0.0,		-1.0,				0.0,				-4.0,				1.0				},		// XComp == 0 (small sphere)
							{	0.0,		-1.0,				0.0,				-4.0,				1.0				},		// XComp == 1 (big sphere)
							{	-1.0,		1.0,				1.0,				3.0,				0.0				},		// XComp == 2 (small rod, variable W)
							{	-1.0,		1.0,				1.0,				3.0,				0.0				},		// XComp == 3 (big rod, variable W)
							{	-1.0,		1.0,				1.0,				3.0,				0.0				},		// XComp == 4 (small rod, fixed W)
							{	-1.0,		1.0,				1.0,				3.0,				0.0				}};		// XComp == 5 (big rod, fixed W);

	//////////////////////////////////////////////////////////////////////////////////////////
	
	//////////////////
	// Constructors //
	//////////////////
	public CModel(String name) {	// Default constructor, includes default values
		this.name = name;
	}
	
	/////////////////
	// Log writing //
	/////////////////
	public void Write(String message, String format, boolean suppressFileOutput) {
		// Construct date and time
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Calendar cal = Calendar.getInstance();
		// Extract format from input arguments
		String prefix = "   ";
		String suffix = "";
		if(format.equalsIgnoreCase("iter")) 	{suffix = " (" + growthIter + "/" + movementIter + ")";} 	else
			if(format.equalsIgnoreCase("warning")) 	{prefix = " WARNING: ";} 									else
				if(format.equalsIgnoreCase("error")) 	{prefix = " ERROR: ";}
		String string = dateFormat.format(cal.getTime()) + prefix + message + suffix;
		// Write to console
		System.out.println(string);
		// Write to file
		if(!suppressFileOutput) {
			try {
				if(!(new File(name)).exists()) {
					new File(name).mkdir();
				}
				if(!(new File(name + "/output")).exists()) {
					new File(name + "/output").mkdir();
				}
				PrintWriter fid = new PrintWriter(new FileWriter(name + "/" + "logfile.txt",true));		// True is for append
				fid.println(string);
				fid.close();
			} catch(IOException E) {
				E.printStackTrace();
			}
		}
	}
	
	public void Write(String message, String format) {
		Write(message,format,false);
	}
	
	//////////////////////////
	// Collision detection  //
	//////////////////////////
	public ArrayList<CCell> DetectFloorCollision(double touchFactor) {				// actual distance < dist*radius--> collision    
		ArrayList<CCell> collisionCell = new ArrayList<CCell>();
		for(CCell cell : cellArray) {
			int NBall = (cell.type<2) ? 1 : 2;	// Figure out number of balls based on type
			for(int iBall=0; iBall<NBall; iBall++) {
				CBall ball = cell.ballArray[iBall];
				if(ball.pos.y - touchFactor*ball.radius < 0) {
					collisionCell.add(cell);
					break;
				}
			}
		}
		return collisionCell;
	}
	
	public ArrayList<CCell> DetectCellCollision_Simple(double touchFactor) {			// Using ArrayList, no idea how big this one will get
		ArrayList<CCell> collisionCell = new ArrayList<CCell>();
		
		for(int iBall=0; iBall<ballArray.size(); iBall++) {						// If we stick to indexing, it'll be easier to determine which cells don't need to be analysed
			CBall ball = ballArray.get(iBall);
			for(int iBall2 = iBall+1; iBall2<ballArray.size(); iBall2++) {
				CBall ball2 = ballArray.get(iBall2);
				if(ball.cell.Index()!=ball2.cell.Index()) {
					Vector3d diff = ball2.pos.minus(ball.pos);
					if(Math.abs(diff.norm()) - touchFactor*(ball.radius+ball2.radius) < 0) {
						collisionCell.add(ball.cell);
						collisionCell.add(ball2.cell);
					}
				}
			}
		}
		return collisionCell;
	}
	
	public ArrayList<CCell> DetectCellCollision_Proper(double touchFactor) {
		ArrayList<CCell> collisionCell = new ArrayList<CCell>();
		
		int NCell = cellArray.size();
		for(int ii=0; ii<NCell; ii++) {
			CCell cell0 = cellArray.get(ii);
			for(int jj=ii+1; jj<NCell; jj++) {
				CCell cell1 = cellArray.get(jj);
				double R2 = cell0.ballArray[0].radius + cell1.ballArray[0].radius; 
				if(cell0.type < 2 && cell1.type < 2) {
					double dist = cell1.ballArray[0].pos.minus(cell0.ballArray[0].pos).norm();
					if(dist<R2*touchFactor) {
						collisionCell.add(cell0); 
						collisionCell.add(cell1);
					}
				} else {
					double H2;
					Vector3d diff = cell1.ballArray[0].pos.minus(cell0.ballArray[0].pos);;
					if(cell0.type > 1 && cell1.type > 1) {
						H2 = aspect[cell0.type]*2.0*cell0.ballArray[0].radius + aspect[cell1.type]*2.0*cell1.ballArray[0].radius + R2;		// aspect0*2*R0 + aspect1*2*R1 + R0 + R1
					} else {
						CCell rod;
						if(cell0.type<2) {
							rod=cell1;
						} else {
							rod=cell0;
						}
						H2 = aspect[rod.type]*2.0*rod.ballArray[0].radius + R2;// H2 is maximum allowed distance with still change to collide: R0 + R1 + 2*R1*aspect
					}
					if(Math.abs(diff.x)<H2 && Math.abs(diff.z)<H2 && Math.abs(diff.y)<H2) {
						double dist = diff.norm();
						if(dist<R2*touchFactor)	{
							collisionCell.add(cell0); 
							collisionCell.add(cell1);
						}
					}
				}
			}
		}
		return collisionCell;
	}
	
	// Ericson collision detection
	
	private double Clamp(double n, double min, double max) {
		if(n<min)	return min;
		if(n>max) 	return max;
		return n;
	}
		
	// Collision detection rod-rod
	public EricsonObject DetectLinesegLineseg(Vector3d p1, Vector3d q1, Vector3d p2, Vector3d q2) {		// This is line segment - line segment collision detection. 
		// Rewritten 120912 because of strange results with the original function
		// Computes closest points C1 and C2 of S1(s) = P1+s*(Q1-P1) and S2(t) = P2+t*(Q2-P2)
		Vector3d d1 = q1.minus(p1);		// Direction of S1
		Vector3d d2 = q2.minus(p2);		// Direction of S2
		Vector3d r = p1.minus(p2);
		double a = d1.dot(d1);			// Squared length of S1, >0
		double e = d2.dot(d2);			// Squared length of S2, >0
		double f = d2.dot(r);
		double c = d1.dot(r);
		double b = d1.dot(d2);
		double denom = a*e-b*b;			// Always >0
		
		// If segments are not parallel, compute closts point on L1 to L2 and clamp to segment S1, otherwise pick arbitrary s (=0)
		double s;
		if(denom!=0.0) {
			s = Clamp((b*f-c*e) /  denom, 0.0, 1.0);
		} else	s = 0.0;
		// Compute point on L2 closest to S1(s) using t = ((P1+D1*s) - P2).dot(D2) / D2.dot(D2) = (b*s + f) / e
		double t = (b*s + f) / e;
		
		// If t is in [0,1] done. Else Clamp(t), recompute s for the new value of t using s = ((P2+D2*t) - P1).dot(D1) / D1.dot(D1) = (t*b - c) / a and clamp s to [0,1]
		if(t<0.0) {
			t = 0.0;
			s = Clamp(-c/a, 0.0, 1.0);
		} else if (t>1.0) {
			t = 1.0;
			s = Clamp((b-c)/a, 0.0, 1.0);
		}
		
		Vector3d c1 = p1.plus(d1.times(s));
		Vector3d c2 = p2.plus(d2.times(t));
		
		// Get the difference of the two closest points
//		Vector3d dP = r.plus(c1.times(s)).minus(c2.times(t));  // = S1(sc) - S2(tc)
		Vector3d dP = c1.minus(c2);  // = S1(sc) - S2(tc)
		
		double dist2 = (c1.minus(c2)).dot(c1.minus(c2));
		
		return new EricsonObject(dP, Math.sqrt(dist2), s, t, c1, c2);
	}
	
	// Collision detection ball-rod
	public EricsonObject DetectLinesegPoint(Vector3d p1, Vector3d q1, Vector3d p2) {
		Vector3d ab = q1.minus(p1);  	// line
		Vector3d w = p2.minus(p1);	//point-line
		//Project c onto ab, computing parameterized position d(t) = a + t*(b-a)
		double rpos = w.dot(ab)/ab.dot(ab);
		//if outside segment, clamp t and therefore d to the closest endpoint
		if ( rpos<0.0 ) rpos = 0.0;
		if ( rpos>1.0 ) rpos = 1.0;
		//compute projected position from the clamped t
		Vector3d d = p1.plus(ab.times(rpos));
		//calculate the vector p2 --> d
		Vector3d dP = d.minus(p2);
		EricsonObject R = new EricsonObject(dP, dP.norm(), rpos);	// Defined at the end of the model class
		return R;
	}
	
	///////////////////////////////
	// Spring breakage detection //
	///////////////////////////////
	public ArrayList<CAnchorSpring> DetectAnchorBreak(double minStretch, double maxStretch) {
		ArrayList<CAnchorSpring> breakArray = new ArrayList<CAnchorSpring>();
		
		for(CAnchorSpring pSpring : anchorSpringArray) {
			double al = (pSpring.ballArray[0].pos.minus(pSpring.anchor)).norm();		// al = Actual Length
			if(al < minStretch*pSpring.restLength || al > maxStretch*pSpring.restLength) {
				breakArray.add(pSpring);
			}
		}
		return breakArray;
	}
	
	public ArrayList<CSpring> DetectStickBreak(double minStretch, double maxStretch) {
		ArrayList<CSpring> breakArray = new ArrayList<CSpring>();
		
		int iSpring = 0;
		while(iSpring < stickSpringArray.size()) {
			CSpring spring = stickSpringArray.get(iSpring);
			double al = (spring.ballArray[1].pos.minus(  spring.ballArray[0].pos)  ).norm();		// al = Actual Length
			if(al < minStretch*spring.restLength || al > maxStretch*spring.restLength) {
				breakArray.add(spring);
			}
			iSpring += spring.siblingArray.size()+1;
		}
		return breakArray;
	} 

	////////////////////
	// Movement stuff //
	////////////////////
	public int Movement() throws Exception {
		// Reset counter
		Assistant.NAnchorBreak = Assistant.NAnchorForm = Assistant.NFilBreak = Assistant.NStickBreak = Assistant.NStickForm = 0;
		
		int nvar = 6*ballArray.size();
		int ntimes = (int) (movementTimeStepEnd/movementTimeStep);
		double atol = 1.0e-6, rtol = atol;
		double h1 = 0.00001, hmin = 0;
		double t1 = movementTime; 
		double t2 = t1 + movementTimeStepEnd;
		Vector ystart = new Vector(nvar,0.0);

		int ii=0;											// Determine initial value vector
		for(CBall ball : ballArray) { 
			ystart.set(ii++, ball.pos.x);
			ystart.set(ii++, ball.pos.y);
			ystart.set(ii++, ball.pos.z);
			ystart.set(ii++, ball.vel.x);
			ystart.set(ii++, ball.vel.y);
			ystart.set(ii++, ball.vel.z);
		}
		Output<StepperDopr853> out = new Output<StepperDopr853>(ntimes);
		feval dydt = new feval(this);
		Odeint<StepperDopr853> ode = new Odeint<StepperDopr853>(ystart, t1, t2, atol, rtol, h1, hmin, out, dydt, this);
		int nstp = ode.integrate();
		for(int iTime=0; iTime<out.nsave; iTime++) {		// Save all intermediate results to the save variables
			int iVar = 0;
			for(CBall ball : ballArray) {
				ball.posSave[iTime].x = out.ysave.get(iVar++,iTime);
				ball.posSave[iTime].y = out.ysave.get(iVar++,iTime);
				ball.posSave[iTime].z = out.ysave.get(iVar++,iTime);
				ball.velSave[iTime].x = out.ysave.get(iVar++,iTime);
				ball.velSave[iTime].y = out.ysave.get(iVar++,iTime);
				ball.velSave[iTime].z = out.ysave.get(iVar++,iTime);
			}
		}
		{int iVar = 0;										// Only the final value is stored in the pos and vel variables
		int iTime = out.nsave;
		for(CBall ball : ballArray) {
			ball.pos.x = out.ysave.get(iVar++,iTime);
			ball.pos.y = out.ysave.get(iVar++,iTime);
			ball.pos.z = out.ysave.get(iVar++,iTime);
			ball.vel.x = out.ysave.get(iVar++,iTime);
			ball.vel.y = out.ysave.get(iVar++,iTime);
			ball.vel.z = out.ysave.get(iVar++,iTime);
		}}
		return nstp;
	}
	
	public Vector CalculateForces(double t, Vector yode) {	
		// Read data from y
		{int ii=0; 				// Where we are in yode
		for(CBall ball : ballArray) {
			ball.pos.x = 	yode.get(ii++);
			ball.pos.y = 	yode.get(ii++);
			ball.pos.z = 	yode.get(ii++);
			ball.vel.x = 	yode.get(ii++);
			ball.vel.y = 	yode.get(ii++);
			ball.vel.z = 	yode.get(ii++);
			ball.force.x = 0;	// Clear forces for first use
			ball.force.y = 0;
			ball.force.z = 0;
		}}
		// Collision forces
		for(int iCell=0; iCell<cellArray.size(); iCell++) {
			CCell cell0 = cellArray.get(iCell);
			CBall c0b0 = cell0.ballArray[0];
			// Base collision on the cell type
			if(cell0.type<2) {
				// Check for all remaining cells
				for(int jCell=iCell+1; jCell<cellArray.size(); jCell++) {
					CCell cell1 = cellArray.get(jCell);
					CBall c1b0 = cell1.ballArray[0];
					double R2 = c0b0.radius + c1b0.radius;
					Vector3d dirn = c0b0.pos.minus(c1b0.pos);
					if(cell1.type<2) {										// The other cell is a ball too
						double dist = dirn.norm();						// Mere estimation for ball-rod
						// do a simple collision detection if close enough
						if(dist<R2) {
							// We have a collision
							dirn.normalise();
							double nBallAvg = (nBallInit[cell0.type]+nBallInit[cell1.type])/2.0;
							Vector3d Fs = dirn.times(Kc*nBallAvg*(R2*1.01-dist));	// Add *1.01 to R2 to give an extra push at collisions (prevent asymptote at touching)
							// Add forces
							c0b0.force = c0b0.force.plus(Fs);
							c1b0.force = c1b0.force.minus(Fs);
						}
					} else {												// this cell is a ball, the other cell is a rod
						double H2 = aspect[cell1.type]*2.0*c1b0.radius + R2;// H2 is maximum allowed distance with still change to collide: R0 + R1 + 2*R1*aspect
						if(dirn.x<H2 && dirn.z<H2 && dirn.y<H2) {
							// do a sphere-rod collision detection
							CBall c1b1 = cell1.ballArray[1];
							EricsonObject C = DetectLinesegPoint(c1b0.pos, c1b1.pos, c0b0.pos);
							Vector3d dP = C.dP;
							double dist = C.dist;							// Make distance more accurate
							double sc = C.sc;
							// Collision detection
							if(dist<R2) {
								double nBallAvg = (nBallInit[cell0.type]+nBallInit[cell1.type])/2.0;
								double f = Kc*nBallAvg / dist*(dist-R2*1.01);
								Vector3d Fs = dP.times(f);
								// Add these elastic forces to the cells
								double sc1 = 1-sc;
								// both balls in rod
								c1b0.force.subtract(Fs.times(sc1));
								c1b1.force.subtract(Fs.times(sc));
								// ball in sphere
								c0b0.force.add(Fs);
							}	
						}
					}
				}
			} else {	// cell.type > 1
				CBall c0b1 = cell0.ballArray[1];
				for(int jCell = iCell+1; jCell<cellArray.size(); jCell++) {
					CCell cell1 = cellArray.get(jCell);
					CBall c1b0 = cell1.ballArray[0];
					double R2 = c0b0.radius + c1b0.radius;
					Vector3d dirn = c0b0.pos.minus(c1b0.pos);
					if(cell1.type<2) {										// This cell is a rod, the Next is a ball
						double H2 = aspect[cell0.type]*2.0*c0b0.radius + R2;// H2 is maximum allowed distance with still change to collide: R0 + R1 + 2*R1*aspect
						if(dirn.x<H2 && dirn.z<H2 && dirn.y<H2) {
							// do a rod-sphere collision detection
							EricsonObject C = DetectLinesegPoint(c0b0.pos, c0b1.pos, c1b0.pos); 
							Vector3d dP = C.dP;
							double dist = C.dist;
							double sc = C.sc;
							// Collision detection
							if(dist < R2) {
								double MBallAvg = (nBallInit[cell0.type]+nBallInit[cell1.type])/2.0;
								double f = Kc*MBallAvg / dist*(dist-R2*1.01);
								Vector3d Fs = dP.times(f);
								// Add these elastic forces to the cells
								double sc1 = 1-sc;
								// both balls in rod
								c0b0.force.subtract(Fs.times(sc1));
								c0b1.force.subtract(Fs.times(sc));
								// ball in sphere
								c1b0.force.add(Fs);
							}	
						}
					} else {	// type>1 --> the other cell is a rod too. This is where it gets tricky
						Vector3d c0b0pos = new Vector3d(c0b0.pos);
						Vector3d c0b1pos = new Vector3d(c0b1.pos);
						Vector3d c1b0pos = new Vector3d(c1b0.pos);
						CBall c1b1 = cell1.ballArray[1];
						Vector3d c1b1pos = new Vector3d(c1b1.pos);
						double H2 = aspect[cell0.type]*2.0*c0b0.radius + aspect[cell1.type]*2.0*c1b0.radius + R2;		// aspect0*2*R0 + aspect1*2*R1 + R0 + R1
						if(dirn.x<H2 && dirn.z<H2 && dirn.y<H2) {
							// calculate the distance between the two diatoma segments
							EricsonObject C = DetectLinesegLineseg(c0b0pos, c0b1pos, c1b0pos, c1b1pos);
							Vector3d dP = C.dP;					// dP is vector from closest point 2 --> 1
							double dist = C.dist;
							double sc = C.sc;
							double tc = C.tc;
							if(dist<R2) {
								double nBallAvg = (nBallInit[cell0.type]+nBallInit[cell1.type])/2.0;
								double f = Kc*nBallAvg / dist*(dist-R2*1.01);
								Vector3d Fs = dP.times(f);
								// Add these elastic forces to the cells
								double sc1 = 1-sc;
								double tc1 = 1-tc;
								// both balls in 1st rod
								c0b0.force.subtract(Fs.times(sc1));
								c0b1.force.subtract(Fs.times(sc));
								// both balls in 1st rod
								c1b0.force.add(Fs.times(tc1));
								c1b1.force.add(Fs.times(tc));
							}
						}
					}
				}
			}
		}
		// Calculate gravity+bouyancy, normal forces and drag
		for(CBall ball : ballArray) {
			// Contact forces
			double y = ball.pos.y;
			double r = ball.radius;
			if(y<r){
				ball.force.y += Kw*nBallInit[ball.cell.type]*(r-y);
			}
			// Gravity and buoyancy
			if(gravity) {
				if(gravityZ) {
					ball.force.z += G * (rhoX-rhoWater) * ball.n*MWX/rhoX;
				} else if(y>r*1.1) {			// Only if not already at the floor plus a tiny bit 
					ball.force.y += G * (rhoX-rhoWater) * ball.n*MWX/rhoX;  //let the ball fall. Note that G is negative 
				}
			}
			
			// Velocity damping
			ball.force.subtract(ball.vel.times(Kd*nBallInit[ball.cell.type]));			// TODO Should be v^2
		}
		
		// Elastic forces between springs within cells (CSpring in type>1)
		for(CSpring rod : rodSpringArray) {
			CBall ball0 = rod.ballArray[0];
			CBall ball1 = rod.ballArray[1];
			// find difference vector and distance dn between balls (euclidian distance) 
			Vector3d diff = ball1.pos.minus(ball0.pos);
			double dn = diff.norm();
			// Get force
			double f = rod.K/dn * (dn - rod.restLength);
			// Hooke's law
			Vector3d Fs = diff.times(f);
			// apply forces on balls
			ball0.force.add(Fs);
			ball1.force.subtract(Fs);
		}
		
		// Apply forces due to anchor springs
		for(CAnchorSpring spring : anchorSpringArray) {
			Vector3d diff = spring.anchor.minus(spring.ballArray[0].pos);
			double dn = diff.norm();
			// Get force
			double f = spring.K/dn * (dn - spring.restLength);
			// Hooke's law
			Vector3d Fs = diff.times(f);
			// apply forces on balls
			spring.ballArray[0].force.add(Fs);

		}
		
		// Apply forces on sticking springs
		for(CSpring stick : stickSpringArray) {
			CBall ball0 = stick.ballArray[0];
			CBall ball1 = stick.ballArray[1];
			// find difference vector and distance dn between balls (euclidian distance) 
			Vector3d diff = ball1.pos.minus(ball0.pos);
			double dn = diff.norm();
			// Get force
			double f = stick.K/dn * (dn - stick.restLength);
			// Hooke's law
			Vector3d Fs = diff.times(f);
			// apply forces on balls
			ball0.force.add(Fs);
			ball1.force.subtract(Fs);
		}
		
		// Filament spring elastic force (CSpring in filSpringArray)
		for(CSpring fil : filSpringArray) {
			CBall ball0 = fil.ballArray[0];
			CBall ball1 = fil.ballArray[1];
			{// find difference vector and distance dn between balls (euclidian distance) 
			Vector3d diff = ball1.pos.minus(ball0.pos);
			double dn = diff.norm();
			// Get force
			double f = fil.K/dn * (dn - fil.restLength);
			// Hooke's law
			Vector3d Fs = diff.times(f);
			// apply forces on balls
			ball0.force.add(Fs);
			ball1.force.subtract(Fs);
			}
		}
		
		// Return results
		Vector dydx = new Vector(yode.size());
		int ii=0;
		for(CBall ball : ballArray) {
				double M = ball.n*MWX;
				dydx.set(ii++,ball.vel.x);			// dpos/dt = v;
				dydx.set(ii++,ball.vel.y);
				dydx.set(ii++,ball.vel.z);
				dydx.set(ii++,ball.force.x/M);		// dvel/dt = a = f/M
				dydx.set(ii++,ball.force.y/M);
				dydx.set(ii++,ball.force.z/M);
		}
		return dydx;
	}
	

	public void AnchorUnAnchor() {
		// See what we need to anchor or break
		for(CCell cell : cellArray) {
			CBall ball0 = cell.ballArray[0];
			CBall ball1 = (cell.type>1) ? ball1 = cell.ballArray[1] : null;

			if(cell.anchorSpringArray.length>0) { 		// This cell is already anchored
				for(CAnchorSpring spring : cell.anchorSpringArray) {
					// Break anchor?
					Vector3d diff = spring.anchor.minus(spring.ballArray[0].pos);
					double dn = diff.norm();
					if(dn < spring.restLength*stretchLimAnchor[0] || dn > spring.restLength*stretchLimAnchor[1]) {			// too much tension || compression --> break the spring
						Assistant.NAnchorBreak += spring.UnAnchor();
					}
				}
			} else {									// Cell is not yet anchored
				// Form anchor?
				boolean formBall0 = (ball0.pos.y < ball0.radius*formLimAnchor) ? true : false;
				boolean formBall1 = false;
				if(cell.type > 1) 	formBall1 = (ball1.pos.y < ball1.radius*formLimAnchor) ? true : false;			// If ball1 != null
				if(formBall0 || formBall1) {
					Assistant.NAnchorForm += cell.Anchor();					
				}
			}
		}
	}
	
	public void StickUnStick() {
		for(int ii=0; ii<cellArray.size(); ii++) {
			CCell cell0 = cellArray.get(ii);
			for(int jj=ii+1; jj<cellArray.size(); jj++) {
				CCell cell1 = cellArray.get(jj);
				// Determine current distance
				CBall c0b0 = cell0.ballArray[0];
				CBall c1b0 = cell1.ballArray[0];
				// Are these cells stuck to each other?
				boolean stuck = false;
				CSpring stickingSpring = null; 
				for(CSpring spring : stickSpringArray) {
					if( (spring.ballArray[0].cell.equals(cell0) && spring.ballArray[1].cell.equals(cell1)) || (spring.ballArray[0].cell.equals(cell1) && spring.ballArray[1].cell.equals(cell0)) ) {
						// That's the one containing both cells
						stuck = true;
						stickingSpring = spring;
						break;						
					}
				}
				if(stuck) {					// Stuck --> can we break this spring (and its siblings)?
					double dist = (c1b0.pos.minus(c0b0.pos)).norm();
					if(dist < stickingSpring.restLength*stretchLimStick[0] || dist > stickingSpring.restLength*stretchLimStick[1]) 		Assistant.NStickBreak += stickingSpring.Break();
				} else {					// Not stuck --> can we stick them?
					boolean related = ((cell0.mother!=null && cell0.mother.equals(cell1)) || (cell1.mother!=null && cell1.mother.equals(cell0))) ? true : false;
					if(!related) {// Check only if these cells are not connected through filament springs
						double R2 = c0b0.radius + c1b0.radius;
						Vector3d dirn = (c1b0.pos.minus(c0b0.pos));
						if(cell0.type<2 && cell1.type<2) {							// both spheres
							double dist = (c1b0.pos.minus(c0b0.pos)).norm();
							if(dist<R2*formLimStick)		Assistant.NStickForm += cell0.Stick(cell1);
						} else if(cell0.type<2) {									// 1st sphere, 2nd rod
							double H2f = formLimStick * aspect[cell1.type]*2.0*c1b0.radius + R2;	// H2 is maximum allowed distance with still change to collide: R0 + R1 + 2*R1*aspect
							if(dirn.x<H2f && dirn.z<H2f && dirn.y<H2f) {
								CBall c1b1 = cell1.ballArray[1];
								// do a sphere-rod collision detection
								EricsonObject C = DetectLinesegPoint(c1b0.pos, c1b1.pos, c0b0.pos);
								if(C.dist<R2*formLimStick) 	Assistant.NStickForm += cell0.Stick(cell1);
							}
						} else if (cell1.type<2) {									// 2nd sphere, 1st rod
							double H2f = formLimStick * aspect[cell0.type]*2.0*c0b0.radius + R2;	// H2 is maximum allowed distance with still change to collide: R0 + R1 + 2*R1*aspect
							if(dirn.x<H2f && dirn.z<H2f && dirn.y<H2f) {
								CBall c0b1 = cell0.ballArray[1];
								// do a sphere-rod collision detection
								EricsonObject C = DetectLinesegPoint(c0b0.pos, c0b1.pos, c1b0.pos);
								if(C.dist<R2*formLimStick) 	Assistant.NStickForm += cell0.Stick(cell1);		// OPTIMISE by passing dist to Stick
							}
						} else {													// both rod
							double H2f = formLimStick * aspect[cell0.type]*2.0*c0b0.radius + aspect[cell1.type]*2.0*c1b0.radius + R2;		// aspect0*2*R0 + aspect1*2*R1 + R0 + R1
							if(dirn.x<H2f && dirn.z<H2f && dirn.y<H2f) {
								CBall c0b1 = cell0.ballArray[1];
								CBall c1b1 = cell1.ballArray[1];
								// calculate the distance between the two diatoma segments
								EricsonObject C = DetectLinesegLineseg(c0b0.pos, c0b1.pos, c1b0.pos, c1b1.pos);
								if(C.dist<R2*formLimStick) 	Assistant.NStickForm += cell0.Stick(cell1);
							}
						}	
					}
				}
			}
		}
	}
	
	public void FilUnFil() {
		ArrayList<CSpring> unFilArray = new ArrayList<CSpring>(0);
		for(CSpring fil : filSpringArray) {
			// No need to make filial links here, is done in growth step
			
			// Filament spring breaking
			double distance = fil.ballArray[0].pos.minus(fil.ballArray[1].pos).norm();
			// Check if we can break this spring
			if(distance<fil.restLength*stretchLimFil[0] || distance>fil.restLength*stretchLimFil[1]) {
				unFilArray.add(fil);
				Assistant.NFilBreak++;
			}
		}
		// unFil all the springs that are detected to be broken, with their siblings 
		for(CSpring fil : unFilArray) fil.Break();
	}
	
	//////////////////
	// Growth stuff //
	//////////////////
	public int GrowthSimple() {						// Growth based on a random number, further enhanced by being sticked to a cell of other type (==0 || !=0) 
		int newCell = 0;
		int NCell = cellArray.size();
		for(int iCell=0; iCell<NCell; iCell++){
			CCell mother = cellArray.get(iCell);
			double amount = mother.GetAmount();

			// Random growth
			amount *= (0.95+rand.Double()/5.0);
			mother.SetAmount(amount);
			
			// Cell growth or division
			if(mother.GetAmount()>nCellMax[mother.type]) {
				newCell++;
				GrowCell(mother);
			}	
		}
		return newCell;
	}
	
	public int GrowthSyntrophy() {						// Growth based on a random number, further enhanced by being sticked to a cell of other type (==0 || !=0) 
		int newCell = 0;
		int NCell = cellArray.size();
		for(int iCell=0; iCell<NCell; iCell++){
			CCell mother = cellArray.get(iCell);
			double mass = mother.GetAmount();

			// Random growth
			mass *= (0.95+rand.Double()/5.0);
			// Syntrophic growth
			for(CCell stickCell : mother.stickCellArray) {
				if((mother.type<2 && stickCell.type>1) || (mother.type>1 && stickCell.type<2)) {
					// The cell types are different on the other end of the spring
					mass *= 1.2;
					break;
				}
			}
			mother.SetAmount(mass);
			
			// Cell growth or division
			if(mother.GetAmount()>nCellMax[mother.type]) {
				newCell++;
				GrowCell(mother);
			}
		}
		return newCell;
	}
	
	public int GrowthFlux() {
		int newCell=0;
		int NCell = cellArray.size();
		for(int iCell=0; iCell<NCell; iCell++){
			CCell cell = cellArray.get(iCell);
			// Obtain mol increase based on flux
			double molIn = cell.q * cell.GetAmount() * growthTimeStep * SMX[cell.type];
			// Grow mother cell
			double newAmount = cell.GetAmount()+molIn;
			cell.SetAmount(newAmount);
			// divide mother cell if ready 
			if(newAmount>nCellMax[cell.type]) {
				newCell++;
				GrowCell(cell);
			}
		}
		return newCell;
	}
	
	public CCell GrowCell(CCell mother) {
		double n = mother.GetAmount();
		CCell daughter;
		if(mother.type<2) {
			// Come up with a nice direction in which to place the new cell
			Vector3d direction = new Vector3d(rand.Double()-0.5,rand.Double()-0.5,rand.Double()-0.5);			
			direction.normalise();
			double displacement = mother.ballArray[0].radius;
			// Make a new, displaced cell
			daughter = new CCell(mother.type,											// Same type as cell
					mother.GetAmount(),
					mother.ballArray[0].pos.x - displacement * direction.x,				// The new location is the old one plus some displacement					
					mother.ballArray[0].pos.y - displacement * direction.y,	
					mother.ballArray[0].pos.z - displacement * direction.z,
					mother.filament,
					mother.colour,
					this);														// Same filament boolean as cell and pointer to the model
			// Set amount for both cells
			daughter.SetAmount(n/2.0);		// Radius is updated in this method
			mother.SetAmount(n/2.0);
			// Set properties for new cell
			daughter.ballArray[0].vel = 	new Vector3d(mother.ballArray[0].vel);
			daughter.ballArray[0].force = 	new Vector3d(mother.ballArray[0].force);
			daughter.colour =				mother.colour;							// copy of reference
			daughter.mother = 				mother;
			daughter.q = 					mother.q;
			// Displace old cell
			mother.ballArray[0].pos = mother.ballArray[0].pos.plus(  direction.times( displacement )  );
			// Contain cells to y dimension of domain
			if(mother.ballArray[0].pos.y 	< mother.ballArray[0].radius) 		{mother.ballArray[0].pos.y 	= mother.ballArray[0].radius;};
			if(daughter.ballArray[0].pos.y < daughter.ballArray[0].radius)  	{daughter.ballArray[0].pos.y 	= daughter.ballArray[0].radius;};
			// Set filament springs
			if(daughter.filament) {
				double K = Kf*(nBallInit[mother.type] + nBallInit[daughter.type])/2.0;
				CSpring fil = new CSpring(mother.ballArray[0], daughter.ballArray[0], K, 0.0, 2);
				fil.ResetRestLength();
			}
		} else {
			CBall motherBall0 = mother.ballArray[0];
			CBall motherBall1 = mother.ballArray[1];
			// Direction
			Vector3d direction = motherBall1.pos.minus( motherBall0.pos );
			direction.normalise();
			// Displacement
			double displacement = motherBall1.radius/2.0;
			// Half mass of mother cell
			mother.SetAmount(mother.GetAmount()/2.0);
			// Make a new, displaced cell
			Vector3d middle = motherBall1.pos.plus(motherBall0.pos).divide(2.0); 
			daughter = new CCell(mother.type,													// Same type as cell
					mother.GetAmount(),															// Same mass as (already slimmed down) mother cell
					middle.x+	  displacement*direction.x,										// First ball. First ball and second ball were swapped in MATLAB and possibly C++					
					middle.y+1.01*displacement*direction.y,										// ought to be displaced slightly in original C++ code but is displaced significantly this way (change 1.01 to 2.01)
					middle.z+	  displacement*direction.z,
					motherBall1.pos.x,															// Second ball
					motherBall1.pos.y,
					motherBall1.pos.z,
					mother.filament,
					mother.colour,
					this);																		// Same filament boolean as cell and pointer to the model
			// Displace old cell, 2nd ball
			motherBall1.pos = middle.minus(direction.times(displacement));
			mother.rodSpringArray.get(0).ResetRestLength();
			// Contain cells to y dimension of domain
			for(int iBall=0; iBall<2; iBall++) {
				if(mother.ballArray[iBall].pos.y 		< mother.ballArray[iBall].radius) 		{mother.ballArray[0].pos.y 	= mother.ballArray[0].radius;};
				if( daughter.ballArray[iBall].pos.y 	< daughter.ballArray[iBall].radius) 	{daughter.ballArray[0].pos.y 	= daughter.ballArray[0].radius;};
			}
			// Set properties for new cell
			for(int iBall=0; iBall<2; iBall++) {
				daughter.ballArray[iBall].vel = 	new Vector3d(mother.ballArray[iBall].vel);
				daughter.ballArray[iBall].force = 	new Vector3d(mother.ballArray[iBall].force);
			}
			daughter.colour =	mother.colour;
			daughter.mother = 	mother;
			daughter.motherIndex = mother.Index();
			daughter.rodSpringArray.get(0).restLength = mother.rodSpringArray.get(0).restLength;

			// Set filament springs
			if(daughter.filament) {
				ArrayList<CSpring> donateFilArray = new ArrayList<CSpring>();
				for(CSpring fil : mother.filSpringArray) {
					boolean found=false;
					if( fil.ballArray[0] == motherBall0) {
						fil.ballArray[0] = daughter.ballArray[0];
						found = true;}
					if( fil.ballArray[1] == motherBall0) {
						fil.ballArray[1] = daughter.ballArray[0];
						found = true;}
					if(found) {
						// Mark filament spring for donation from mother to daughter
						donateFilArray.add(fil);
					}
				}
				for(CSpring fil : donateFilArray) {
					daughter.filSpringArray.add(fil);
					mother.filSpringArray.remove(fil);
					// Reset rest lengths
					fil.ResetRestLength();
				}
				// Make new filial link between mother and daughter
				double K = Kf*(nBallInit[mother.type] + nBallInit[daughter.type])/2.0;
				CSpring filSmall = new CSpring(daughter.ballArray[0], mother.ballArray[1], K, 0.0, 2);		// type==2 --> Small spring
				CSpring filBig = new CSpring(daughter.ballArray[1], mother.ballArray[0], K, 0.0, 3);		// type==3 --> Big spring
				filSmall.siblingArray.add(filBig);
				filBig.siblingArray.add(filSmall);
				filSmall.ResetRestLength();			// Big uses small's rest length, so do small first!
				filBig.ResetRestLength();
			}
		}
		return daughter;
	}
	
	////////////////////////////
	// Anchor and Stick stuff //
	////////////////////////////
	public void BreakStick(ArrayList<CSpring> breakArray) {
		for(CSpring spring : breakArray) {
			CCell cell0 = spring.ballArray[0].cell;
			CCell cell1 = spring.ballArray[1].cell;
			// Remove cells from each others' stickCellArray 
			cell0.stickCellArray.remove(cell1);
			cell1.stickCellArray.remove(cell0);
			// Remove springs from model stickSpringArray
			stickSpringArray.remove(spring);
			for(int ii=0; ii<spring.siblingArray.size(); ii++) {
				stickSpringArray.remove(spring.siblingArray.get(ii));
			}
		}
	}
	
	public int BuildAnchor(ArrayList<CCell> collisionArray) {
		// Make unique
		for(CAnchorSpring pSpring : anchorSpringArray) collisionArray.remove(pSpring.ballArray[0].cell);
		
		// Anchor the non-stuck, collided cells to the ground
		for(CCell cell : collisionArray) cell.Anchor();
		return anchorSpringArray.size();
	}
	
	public int BuildStick(ArrayList<CCell> collisionArray) {
		int counter = 0;
		for(int ii=0; ii<collisionArray.size(); ii+=2) {		// For loop works per duo
			boolean setStick = true;
			CCell cell0 = collisionArray.get(ii);
			CCell cell1 = collisionArray.get(ii+1);
			// Check if already stuck, don't stick if that is the case
			for(CSpring pSpring : stickSpringArray) {		// This one should update automatically after something new has been stuck --> Only new ones are stuck AND, as a result, only uniques are sticked 
				if((pSpring.ballArray[0].cell.equals(cell0) && pSpring.ballArray[1].cell.equals(cell1)) || (pSpring.ballArray[0].cell.equals(cell1) && pSpring.ballArray[1].cell.equals(cell0))) {
					setStick = false;
				}
			}
			if(setStick) {
				cell0.Stick(cell1);
				counter++;
			}
		}
		return counter;
	}
	
	////////////
	// Saving //
	////////////
	
	public void Save() {		// Save as serialised file, later to be converted to .mat file
		FileOutputStream fos = null;
		GZIPOutputStream gz = null;
		ObjectOutputStream oos = null;
		try {
			fos = new FileOutputStream(String.format("%s/output/g%04dm%04d.ser", name, growthIter, movementIter));
			gz = new GZIPOutputStream(fos);
			oos = new ObjectOutputStream(gz);
			oos.writeObject(this);
			oos.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
}

class EricsonObject {		// Used for collision detection multiple return
	Vector3d dP;
	double dist;
	double sc;
	double tc;
	Vector3d c1;
	Vector3d c2;
	
	EricsonObject(Vector3d dP, double dist, double sc) {
		this.dP = dP;
		this.dist = dist; 
		this.sc = sc;
	}
	
	EricsonObject(Vector3d dP, double dist, double sc, double tc, Vector3d c1, Vector3d c2) {
		this.dP = dP;
		this.dist = dist; 
		this.sc = sc;
		this.tc = tc;
		this.c1 = c1;
		this.c2 = c2;
	}
}