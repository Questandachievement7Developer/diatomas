% Settings
L = [20,20,20];
imageWidth = 1024;
imageHeight = 768;
camPosDifference = [0.0; 0.5*L(2); -L(3)];		% Where the camera will hover compared to camView
ambient = 0.8;
diffuse = 0.4;
keepPOV = true;
% Colours
filColour = [.4 1 1];		% Filament spring is blue
stickColour = [1 .1 1];		% Sticking spring is purple
anchorColour = [.4 .4 .1];		% Anchoring spring is yellow

NSave = length(model.ballArray(1).posSave);
if model.relaxationIter==0 && model.growthIter==0
	NSave = 0;
end
	
if rem(model.relaxationIter,5)==0 || ~exist('camAngle','var')	% Every 5th iteration or when none exists, find a proper angle
	% Create camera, background and lighting based on L
	%%%%%%%%
	%
	%   ^ |  /^
	%   | | //
	% O y |/z   OB
	% ____C__O_________
	%  VD/ x->  &
	% A /
	%  /
	%
	%%%%%%%%
	aspect = imageWidth/imageHeight;
	minPos = min([model.ballArray.pos],[],2)*1e6;		% *1e6 to convert to POVRay coordinates
	maxPos = max([model.ballArray.pos],[],2)*1e6;
	C = (maxPos+minPos)/2;	% Camera is at the plane, right in the middle, height == radius
	camView = C;
	camPos = C + camPosDifference;
	A = camPos;
	% Find vector perpendicular to x axis AND camera axis
	vertAxis = cross(C-A, [1; 0; 0]);
	% Reset ranges
	horRange = 0.0;
	vertRange = 0.0;
	
	for ii=1:length(model.ballArray)
		ball = model.ballArray(ii);
		B = ball.pos*1e6;
		% Find horizontal range (easy)
		horRangeNew = abs(C(1)-ball.pos(1)*1e6 + ball.radius*1e6);
		% Find vertical range (harder)
		% Project position onto vertAxis, convert to right
		BC = C-B;
		projBC = dot(vertAxis, BC)/norm(vertAxis);
		vertRangeNew = abs(projBC)+ball.radius*1e6;
		if horRangeNew>horRange || vertRangeNew>vertRange
			if horRangeNew > vertRangeNew*aspect
				horRange = horRangeNew;
				vertRange = horRangeNew/aspect;
				right = 2*horRange+0.5;						% From which we'll derive up vector
			else
				horRange = vertRangeNew*aspect;
				vertRange = vertRangeNew;
				right = 2*vertRange*aspect+0.5;				% From which we'll derive up vector
			end
		end
	end
end
		
for ii=0:NSave
	imageName{ii+1} = sprintf('pov_g%04dr%04d_%02d', model.growthIter, model.relaxationIter, ii);
	imageLoc{ii+1} = [location '/image/' imageName{ii+1} '.png'];
	povName{ii+1} = [location sprintf('/output/pov_g%04dr%04d_%02d.pov', model.growthIter, model.relaxationIter, ii)];
end
	
for ii=0:NSave			% Can be replaced with parfor
	% Skip half if this is a sketch
	if sketch && rem(ii,2)==1
		continue
	end
	
	if(exist(imageLoc{ii+1},'file'))
% 		fprintf(['File already found, skipping: ' imageName{ii+1} '\n']);
		skip = true;
	else
		skip = false;
	end

	if skip
		continue
	end
	
	if(exist(povName{ii+1},'file'))
		delete(povName{ii+1});
	end
	fid = fopen(povName{ii+1},'a');
	
	if ii~=NSave
		plotIntermediate=true;		% If this is not the first iteration, do the intermediate plotting
	else
		plotIntermediate=false;
	end
	
	fprintf(fid,'#declare Lx = %f;\n',L(1));
	fprintf(fid,'#declare Ly = %f;\n',L(2));
	fprintf(fid,'#declare Lz = %f;\n\n',L(3));
	fprintf(fid,['camera {\n',...
		'\torthographic\n',...
		sprintf('\tlocation <%g, %g, %g>\n', camPos(1), camPos(2), camPos(3)),...
		sprintf('\tlook_at  <%g, %g, %g>\n', camView(1), camView(2), camView(3)),...
		sprintf('\tright <%g, 0.0, 0.0>\n', right),...
		sprintf('\tup <0.0, %g/%g, 0.0>\n', right, aspect),...
		'}\n\n']);
	fprintf(fid,'background { color rgb <1, 1, 1> }\n\n');
	fprintf(fid,'light_source { < 0.0,  2*Ly,  0.0> color rgb <1,1,1> }\n');
	
	% Build spheres and rods
	for iCell=1:length(model.cellArray)
		cell = model.cellArray(iCell);
		fprintf(fid,['// Cell no. ' num2str(iCell-1) '\n']);		% -1 because we want Java numbering
		if cell.type<2
			% Spherical cell
			ball = model.ballArray(cell.ballArray(1)+1);			% +1 because of Java --> MATLAB
			
			if plotIntermediate
				pos = ball.posSave(ii+1,:)*1e6;
			else
				pos = ball.pos*1e6;
			end
			position = sprintf('\t < %10.3f,%10.3f,%10.3f > \n', pos(1), pos(2), pos(3));
			fprintf(fid,['sphere\n',...
				'{\n',...
				position,...
				sprintf('\t%10.3f\n', ball.radius*1e6),...
				'\ttexture{\n',...
				'\t\tpigment{\n',...
				'\t\t\tgradient <0,1,0>\n',...
				sprintf('\t\t\ttranslate <0,%g,0>\n',pos(2)),...		% Keeps the stripes in the correct location
				'\t\t\tcolor_map{\n',...
				sprintf('\t\t\t\t[0.4 color rgb< %10.3f,%10.3f,%10.3f >]', cell.colour(1), cell.colour(2), cell.colour(3)),...
				'\t\t\t\t[0.4 color rgb< 1,1,1 >]\n',...
				'\t\t\t\t[0.6 color rgb< 1,1,1 >]\n',...
				sprintf('\t\t\t\t[0.6 color rgb< %10.3f,%10.3f,%10.3f >]', cell.colour(1), cell.colour(2), cell.colour(3)),...
				'\t\t\t}\n',...
				'\t\t\tfrequency 6',...
				...sprintf('\t\t\tcolor rgb<%10.3f,%10.3f,%10.3f>\n', cell.colour(1), cell.colour(2), cell.colour(3)),...
				'\t\t}\n',...
				'\t\tfinish{\n',...
				['\t\t\tambient ' num2str(ambient) '\n'],...
				['\t\t\tdiffuse ' num2str(diffuse) '\n'],...
				'\t\t}\n',...
				'\t}\n',...
				'}\n\n']);
		elseif cell.type>1	% Rod
			ball = model.ballArray(cell.ballArray(1)+1);				% +1 because of Java --> MATLAB
			ballNext = model.ballArray(cell.ballArray(2)+1);
			
			if plotIntermediate
				position = [sprintf('\t<%10.3f,%10.3f,%10.3f>,\n', ball.posSave(ii+1,1)*1e6, ball.posSave(ii+1,2)*1e6, ball.posSave(ii+1,3)*1e6),...		% +1 because of Java --> MATLAB
					sprintf('\t<%10.3f,%10.3f,%10.3f>,\n', ballNext.posSave(ii+1,1)*1e6, ballNext.posSave(ii+1,2)*1e6, ballNext.posSave(ii+1,3)*1e6)];
				
				position2 = sprintf('\t < %10.3f,%10.3f,%10.3f > \n', ball.posSave(ii+1,1)*1e6, ball.posSave(ii+1,2)*1e6, ball.posSave(ii+1,3)*1e6);
				
				position3 = sprintf('\t < %10.3f,%10.3f,%10.3f > \n', ballNext.posSave(ii+1,1)*1e6, ballNext.posSave(ii+1,2)*1e6, ballNext.posSave(ii+1,3)*1e6);
			else
				position = [sprintf('\t<%10.3f,%10.3f,%10.3f>,\n', ball.pos(1)*1e6, ball.pos(2)*1e6, ball.pos(3)*1e6),...
					sprintf('\t<%10.3f,%10.3f,%10.3f>,\n', ballNext.pos(1)*1e6, ballNext.pos(2)*1e6, ballNext.pos(3)*1e6)];
				
				position2 = sprintf('\t < %10.3f,%10.3f,%10.3f > \n', ball.pos(1)*1e6, ball.pos(2)*1e6, ball.pos(3)*1e6);
				
				position3 = sprintf('\t < %10.3f,%10.3f,%10.3f > \n', ballNext.pos(1)*1e6, ballNext.pos(2)*1e6, ballNext.pos(3)*1e6);
			end
			fprintf(fid,['cylinder\n',...		% Sphere-sphere connection
				'{\n',...
				position,...
				sprintf('\t%10.3f\n', ball.radius*1e6),...
				'\ttexture{\n',...
				'\t\tpigment{\n',...
				sprintf('\t\t\tcolor rgb<%10.3f,%10.3f,%10.3f>\n', cell.colour(1), cell.colour(2), cell.colour(3)),...
				'\t\t}\n',...
				'\t\tfinish{\n',...
				['\t\t\tambient ' num2str(ambient) '\n'],...
				['\t\t\tdiffuse ' num2str(diffuse) '\n'],...
				'\t\t}\n',...
				'\t}\n',...
				'}\n',...
				'sphere\n',...			% First sphere
				'{\n',...
				position2,...
				sprintf('\t%10.3f\n', ball.radius*1e6),...
				'\ttexture{\n',...
				'\t\tpigment{\n',...
				sprintf('\t\t\tcolor rgb<%10.3f,%10.3f,%10.3f>\n', cell.colour(1), cell.colour(2), cell.colour(3)),...
				'\t\t}\n',...
				'\t\tfinish{\n',...
				['\t\t\tambient ' num2str(ambient) '\n'],...
				['\t\t\tdiffuse ' num2str(diffuse) '\n'],...
				'\t\t}\n',...
				'\t}\n',...
				'}\n',...
				'sphere\n',...			% Second sphere
				'{\n',...
				position3,...
				sprintf('\t%10.3f\n', ballNext.radius*1e6),...
				'\ttexture{\n',...
				'\t\tpigment{\n',...
				sprintf('\t\t\tcolor rgb<%10.3f,%10.3f,%10.3f>\n', cell.colour(1), cell.colour(2), cell.colour(3)),...
				'\t\t}\n',...
				'\t\tfinish{\n',...
				['\t\t\tambient ' num2str(ambient) '\n'],...
				['\t\t\tdiffuse ' num2str(diffuse) '\n'],...
				'\t\t}\n',...
				'\t}\n',...
				'}\n\n']);
		end
	end
	
	% Build filament springs
	for iFil = 1:length(model.filSpringArray)
		fil = model.filSpringArray(iFil);
		if fil.type==2
			fprintf(fid,['// Filament spring no. ' num2str(iFil-1) '\n']);
			ball 	= model.ballArray(fil.ballArray(1)+1);
			ballNext = model.ballArray(fil.ballArray(2)+1);

			if plotIntermediate
				position = [sprintf('\t<%10.3f,%10.3f,%10.3f>,\n', ball.posSave(ii+1,1)*1e6, ball.posSave(ii+1,2)*1e6, ball.posSave(ii+1,3)*1e6),...
					sprintf('\t<%10.3f,%10.3f,%10.3f>,\n', ballNext.posSave(ii+1,1)*1e6, ballNext.posSave(ii+1,2)*1e6, ballNext.posSave(ii+1,3)*1e6)];
			else
				position = [sprintf('\t<%10.3f,%10.3f,%10.3f>,\n', ball.pos(1)*1e6, ball.pos(2)*1e6, ball.pos(3)*1e6),...
					sprintf('\t<%10.3f,%10.3f,%10.3f>,\n', ballNext.pos(1)*1e6, ballNext.pos(2)*1e6, ballNext.pos(3)*1e6)];
			end
			fprintf(fid,['cylinder\n',...
				'{\n',...
				position,...
				sprintf('\t%10.3f\n', ball.radius*1e5),...
				'\ttexture{\n',...
				'\t\tpigment{\n',...
				sprintf('\t\t\tcolor rgb<%10.3f,%10.3f,%10.3f>\n', filColour(1), filColour(2), filColour(3)),...
				'\t\t}\n',...
				'\t\tfinish{\n',...
				['\t\t\tambient ' num2str(ambient) '\n'],...
				['\t\t\tdiffuse ' num2str(diffuse) '\n'],...
				'\t\t}\n',...
				'\t}\n',...
				'}\n\n']);
		end
	end
	
	% Build stick spring array
	for iStick = 1:length(model.stickSpringArray)
		fprintf(fid,['// Sticking spring no. ' num2str(iStick-1) '\n']);
		spring = model.stickSpringArray(iStick);
		ball = model.ballArray(spring.ballArray(1)+1);
		ballNext = model.ballArray(spring.ballArray(2)+1);
		
		if plotIntermediate
			position = [sprintf('\t<%10.3f,%10.3f,%10.3f>,\n', ball.posSave(ii+1,1)*1e6, ball.posSave(ii+1,2)*1e6, ball.posSave(ii+1,3)*1e6),...
				sprintf('\t<%10.3f,%10.3f,%10.3f>,\n', ballNext.posSave(ii+1,1)*1e6, ballNext.posSave(ii+1,2)*1e6, ballNext.posSave(ii+1,3)*1e6)];
		else
			position = [sprintf('\t<%10.3f,%10.3f,%10.3f>,\n', ball.pos(1)*1e6, ball.pos(2)*1e6, ball.pos(3)*1e6),...
				sprintf('\t<%10.3f,%10.3f,%10.3f>,\n', ballNext.pos(1)*1e6, ballNext.pos(2)*1e6, ballNext.pos(3)*1e6)];
		end
		fprintf(fid,['cylinder\n',...
			'{\n',...
			position,...
			sprintf('\t%10.3f\n', ball.radius*1e5),...									% 1e5 == 1/10 of the actual ball radius
			'\ttexture{\n',...
			'\t\tpigment{\n',...
			sprintf('\t\t\tcolor rgb<%10.3f,%10.3f,%10.3f>\n',stickColour(1), stickColour(2), stickColour(3)),...
			'\t\t}\n',...
			'\t\tfinish{\n',...
			['\t\t\tambient ' num2str(ambient) '\n'],...
			['\t\t\tdiffuse ' num2str(diffuse) '\n'],...
			'\t\t}\n',...
			'\t}\n',...
			'}\n\n']);
	end
	
	%Build anchor spring array
	for iAnchor = 1:length(model.anchorSpringArray)
		fprintf(fid,['// Anchor spring no. ' num2str(iAnchor-1) '\n']);					% -1 so we use Java numbering to display this
		spring = model.anchorSpringArray(iAnchor);
		
		ball = model.ballArray(spring.ballArray(1)+1);
		if plotIntermediate
			position= sprintf('\t < %10.3f,%10.3f,%10.3f > \n', ball.posSave(ii+1,1)*1e6, ball.posSave(ii+1,2)*1e6, ball.posSave(ii+1,3)*1e6);
		else
			position= sprintf('\t < %10.3f,%10.3f,%10.3f > \n', ball.pos(1)*1e6, ball.pos(2)*1e6, ball.pos(3)*1e6);
		end
		if ~all(round(spring.anchorPoint*1e6/1000)*1000==round(ball.pos*1e6/1000)*1000)	% If equal until 3 decimals, scaled to micron, skip this one because it will produce "degenerate cylinders"
			fprintf(fid,['cylinder\n',...
				'{\n',...
				position,...
				sprintf('\t<%10.3f,%10.3f,%10.3f>,\n', spring.anchorPoint(1)*1e6, spring.anchorPoint(2)*1e6, spring.anchorPoint(3)*1e6),...
				sprintf('\t%10.3f\n', ball.radius*1e5),...	% 1e5 because it is a spring
				'\ttexture{\n',...
				'\t\tpigment{\n',...
				sprintf('\t\t\tcolor rgb<%10.3f,%10.3f,%10.3f>\n', anchorColour(1), anchorColour(2), anchorColour(3)),...		%Anchoring springs are yellow
				'\t\t}\n',...
				'\t\tfinish{\n',...
				['\t\t\tambient ' num2str(ambient) '\n'],...
				['\t\t\tdiffuse ' num2str(diffuse) '\n'],...
				'\t\t}\n',...
				'\t}\n',...
				'}\n\n']);
		end
	end
	
	% Create plane
	if model.normalForce
		fprintf(fid,['union {\n',...
			'\tbox {\n',...
			'\t\t<-Lx, 0, -Lz>\n',...				% Corner 1. Centred around 0.5 Lx and 0.5 Lz
			'\t\t< Lx, 0,  Lz>\n',...				% Corner 2
			'\t\trotate<0.0, 45, 0.0>\n',...
			'\t\ttexture {\n',...
			'\t\t\tpigment {\n',...
			'\t\t\t\tbrick\n',...
			'\t\t\t\tcolor rgb<0, 0, 0>\n',...
			'\t\t\t\tcolor rgb<1, 1, 1>\n',...
			'\t\t\t\tbrick_size<1.0, 1.0, 1.0>\n',...
			'\t\t\t\tmortar 0.025\n',...
			'\t\t\t\ttranslate<0.0 0.0 0.5>\n',...
			'\t\t\t\trotate<0.0 45 0.0>\n',...
			'\t\t\t}\n',...
			'\t\t\tfinish {\n',...
			['\t\t\t\tambient ' num2str(ambient) '\n'],...
			['\t\t\t\tdiffuse ' num2str(diffuse) '\n'],...
			'\t\t\t}\n',...
			'\t\t}\n',...
			'\t}\n',...
			'}\n']);
	end
	% Finalise the file
	fclose(fid);
	
	%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
	if sketch
		systemInput = ['povray ' povName{ii+1} ' +W' num2str(imageWidth/2) ' +H' num2str(imageHeight/2) ' +O' location '/image/' imageName{ii+1} ' +A +Q4'];
	else
		systemInput = ['povray ' povName{ii+1} ' +W' num2str(imageWidth) ' +H' num2str(imageHeight) ' +O' location '/image/' imageName{ii+1} ' +A -J'];
	end
	
	if keepPOV
		remove = '';
	else
		remove = ['rm ' povName{ii+1}];
	end
	[~,message] = system(['cd ' location ' ; ' systemInput ' ; cd ..']);
	% Append text for relaxation and growth
	system(['convert -antialias -pointsize 30 -font courier-bold -annotate 0x0+30+50 ''Growth time:     ' sprintf('%5.2f h',model.growthIter*model.growthTimeStep/3600.0) '\nRelaxation time: ' sprintf('%5.2f s'' ',model.relaxationIter*model.relaxationTimeStep+ii*model.relaxationTimeStep)  imageLoc{ii+1} ' ' imageLoc{ii+1}]);
% 	% Append scale bar
% 	A = camPos;
% 	C = camView;
% 	AC = norm(A-C);
% 	BC = tan(deg2rad(0.5*camAngle))*AC;
% 
% 	LLine = 1/BC * imageWidth/2;
% 
% 	system(['convert -antialias -pointsize 30 -font courier-bold -annotate 0x0+880+50 ''1 um'' ' imageLoc{ii+1} ' ' imageLoc{ii+1}]);
% 	system(['convert -stroke black -strokewidth 3 -draw "line ' num2str(imageWidth-110-LLine/2) ',70 ' num2str(imageWidth-110+LLine/2) ',70" ' imageLoc{ii+1} ' ' imageLoc{ii+1}]);

	% Remove POV file if desired
	[~,~] = system(['cd ' location ' ; ' remove ' ; cd ..']);
end