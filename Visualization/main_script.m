clc;
close all;
clear variables; %clear classes;
rand('state',0); % rand('state',sum(100*clock));
dbstop if error;


%% common setting to read text files

delimiter = ' ';
headerlinesIn = 1;
nanoSecondToSecond = 1000000000;


%% 1) parse ARCore sensor pose data

% parsing ARCore sensor pose data text file
textFileDir = 'ARCore_sensor_pose.txt';
textARCorePoseData = importdata(textFileDir, delimiter, headerlinesIn);
ARCorePoseTime = textARCorePoseData.data(:,1).';
ARCorePoseTime = (ARCorePoseTime - ARCorePoseTime(1)) ./ nanoSecondToSecond;
ARCorePoseRotation = textARCorePoseData.data(:,[5 2 3 4]).';
ARCorePoseTranslation = textARCorePoseData.data(:,[6 7 8]).';

% ARCore sensor pose with various 6-DoF sensor pose representations
numPose = size(ARCorePoseRotation,2);
R_gb_ARCore = zeros(3,3,numPose);
T_gb_ARCore = cell(1,numPose);
stateEsti_ARCore = zeros(6,numPose);
for k = 1:numPose
    
    % rigid body transformation matrix (4x4) (rotation matrix SO(3) from quaternion)
    R_gb_ARCore(:,:,k) = q2r(ARCorePoseRotation(:,k));
    T_gb_ARCore{k} = [R_gb_ARCore(:,:,k), ARCorePoseTranslation(:,k); [0, 0, 0, 1]];
    
    % state vector and rotation matrix
    stateEsti_ARCore(1:3,k) = T_gb_ARCore{k}(1:3,4);
    [yaw, pitch, roll] = dcm2angle(R_gb_ARCore(:,:,k));
    stateEsti_ARCore(4:6,k) = [roll; pitch; yaw];
end

% plot update rate of ARCore sensor pose
timeDifference = diff(ARCorePoseTime);
meanUpdateRate = (1/mean(timeDifference));
figure;
plot(ARCorePoseTime(2:end), timeDifference, 'm'); hold on; grid on; axis tight;
set(gcf,'color','w'); hold off;
axis([min(ARCorePoseTime) max(ARCorePoseTime) min(timeDifference) max(timeDifference)]);
set(get(gcf,'CurrentAxes'),'FontName','Times New Roman','FontSize',17);
xlabel('Time [sec]','FontName','Times New Roman','FontSize',17);
ylabel('Time Difference [sec]','FontName','Times New Roman','FontSize',17);
title(['Mean Update Rate: ', num2str(meanUpdateRate), ' Hz'],'FontName','Times New Roman','FontSize',17);
set(gcf,'Units','pixels','Position',[100 200 1800 900]);  % modify figure


%% 2) parse ARCore point cloud data

% parsing ARCore point cloud data text file
textFileDir = 'ARCore_point_cloud.txt';
textARCorePointData = importdata(textFileDir, delimiter, headerlinesIn);

% ARCore 3D point cloud
ARCorePoints = textARCorePointData.data(:,[1:3]).';
ARCoreColors = textARCorePointData.data(:,[4:6]).';
numPoints = size(ARCorePoints,2);


%% plot ARCore VIO results

% 1) play 3D trajectory of ARCore sensor pose
figure(10);
for k = 1:numPose
    figure(10); cla;
    
    % draw moving trajectory
    p_gb_ARCore = stateEsti_ARCore(1:3,1:k);
    plot3(p_gb_ARCore(1,:), p_gb_ARCore(2,:), p_gb_ARCore(3,:), 'm', 'LineWidth', 2); hold on; grid on; axis equal;
    
    % draw sensor body and frame
    plot_inertial_frame(0.5);
    Rgb_ARCore_current = T_gb_ARCore{k}(1:3,1:3);
    pgb_ARCore_current = T_gb_ARCore{k}(1:3,4);
    plot_sensor_ARCore_frame(Rgb_ARCore_current, pgb_ARCore_current, 0.5, 'm');
    refresh; pause(0.01); k
end


% 2) plot ARCore VIO motion estimation results
figure;
h_ARCore = plot3(stateEsti_ARCore(1,:),stateEsti_ARCore(2,:),stateEsti_ARCore(3,:),'m','LineWidth',2); hold on; grid on;
scatter3(ARCorePoints(1,:), ARCorePoints(2,:), ARCorePoints(3,:), 50*ones(numPoints,1), (ARCoreColors ./ 255).','.');
plot_inertial_frame(0.5); legend(h_ARCore,{'ARCore'}); axis equal; view(26, 73);
xlabel('x [m]','fontsize',10); ylabel('y [m]','fontsize',10); zlabel('z [m]','fontsize',10); hold off;

% figure options
f = FigureRotator(gca());


% 3) plot roll/pitch/yaw of ARCore device orientation
figure;
subplot(3,1,1);
plot(ARCorePoseTime, stateEsti_ARCore(4,:), 'm'); hold on; grid on; axis tight;
set(gcf,'color','w'); hold off;
axis([min(ARCorePoseTime) max(ARCorePoseTime) min(stateEsti_ARCore(4,:)) max(stateEsti_ARCore(4,:))]);
set(get(gcf,'CurrentAxes'),'FontName','Times New Roman','FontSize',17);
xlabel('Time [sec]','FontName','Times New Roman','FontSize',17);
ylabel('Roll [rad]','FontName','Times New Roman','FontSize',17);
subplot(3,1,2);
plot(ARCorePoseTime, stateEsti_ARCore(5,:), 'm'); hold on; grid on; axis tight;
set(gcf,'color','w'); hold off;
axis([min(ARCorePoseTime) max(ARCorePoseTime) min(stateEsti_ARCore(5,:)) max(stateEsti_ARCore(5,:))]);
set(get(gcf,'CurrentAxes'),'FontName','Times New Roman','FontSize',17);
xlabel('Time [sec]','FontName','Times New Roman','FontSize',17);
ylabel('Pitch [rad]','FontName','Times New Roman','FontSize',17);
subplot(3,1,3);
plot(ARCorePoseTime, stateEsti_ARCore(6,:), 'm'); hold on; grid on; axis tight;
set(gcf,'color','w'); hold off;
axis([min(ARCorePoseTime) max(ARCorePoseTime) min(stateEsti_ARCore(6,:)) max(stateEsti_ARCore(6,:))]);
set(get(gcf,'CurrentAxes'),'FontName','Times New Roman','FontSize',17);
xlabel('Time [sec]','FontName','Times New Roman','FontSize',17);
ylabel('Yaw [rad]','FontName','Times New Roman','FontSize',17);
set(gcf,'Units','pixels','Position',[100 200 1800 900]); % modify figure


