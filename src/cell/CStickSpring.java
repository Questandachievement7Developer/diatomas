package cell;

import java.util.ArrayList;

public class CStickSpring {
	CBall[] ballArray = new CBall[2];
//	int[] ballArrayIndex = new int[2];
	double K;
	double restLength;
	CStickSpring[] siblingArray = new CStickSpring[0];
	int NSibling;
	
	///////////////////////////////////////////////////////////////////

	public CStickSpring(CBall ball0, CBall ball1){			// Note that siblingArray is by default not initialised
		CModel model = ball0.cell.model;
		K = model.Ks * (model.MBallInit[ball0.cell.type]+model.MBallInit[ball1.cell.type])/2.0;
		restLength = ball0.radius*Math.max(2.0, model.aspect[ball0.cell.type]) + ball1.radius*Math.max(2.0, model.aspect[ball1.cell.type]);
		ballArray[0] = ball0;
		ballArray[1] = ball1;
		// Set the siblingArray size
		int NSibling;
		if(ball0.cell.type<2 && ball1.cell.type<2) 		{NSibling = 0;}
		else if(ball0.cell.type>1 && ball1.cell.type>1)	{NSibling = 3;}
		else 											{NSibling = 1;}
			
		siblingArray = new CStickSpring[NSibling];
		// Add this sticking spring to the model
		ballArray[0].cell.model.stickSpringArray.add(this);
	}
	
	public CStickSpring () {}								// Empty constructor for model loading. Note that the stickSpring is not automatically added to the array
	
	//////////////////////////////////////////////////////////////////////
	
	public void set(CBall ball0, CBall ball1) {
		ballArray[0] = ball0;
//		ballArrayIndex[0] = ball0.index;
		ballArray[1] = ball1;
//		ballArrayIndex[1] = ball1.index;
	}
	
	public int Index() {
		ArrayList<CStickSpring> array = this.ballArray[0].cell.model.stickSpringArray;
		for(int index=0; index<array.size(); index++) {
			if(array.equals(this))	return index;
		}
		return -1;
	}

} 