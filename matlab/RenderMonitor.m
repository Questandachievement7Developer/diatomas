folderList = dir('../');
folderList = {folderList.name};

for ii=length(folderList):-1:1
	remove = false;
	folderName = folderList{ii};
	if folderName(1)=='.';
		remove = true;
	end
	if ~exist(['../' folderName '/output'],'dir')
		remove = true;
	end
	if remove
		folderList(ii)=[];
	end
end

while true
	for ii=1:length(folderList)
		folderName = folderList{ii};
		disp([datestr(now) '  ' folderName]);
		location = ['../' folderName];
		Render;
	end
	pause(10);
end