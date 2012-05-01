package cell;

import java.util.ArrayList;

public class CStickSpring {
	CBall[] ballArray = new CBall[2];
	double K;
	double restLength;
	ArrayList<CStickSpring> siblingArray;					// TODO: get rid of siblingArray, make CStickSpring of size 2*siblingArray.size()
	double stickArrayIndex;
	
	public CStickSpring(CBall ball1, CBall ball2){			// Note that siblingArray is by default not initialised
		CModel pModel = ball1.pCell.pModel;
		K = pModel.Ks;
		restLength = ball1.radius * pModel.aspect * 2;
		ballArray[0] = ball1;
		ballArray[1] = ball2;
		stickArrayIndex = ballArray[0].pCell.pModel.stickSpringArray.size();
		ballArray[0].pCell.pModel.stickSpringArray.add(this);
	}
}
