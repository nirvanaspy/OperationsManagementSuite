package com.rengu.operationsoanagementsuite.Service;

import com.rengu.operationsoanagementsuite.Entity.DeviceEntity;
import com.rengu.operationsoanagementsuite.Entity.HeartbeatEntity;
import com.rengu.operationsoanagementsuite.Exception.CustomizeException;
import com.rengu.operationsoanagementsuite.Repository.DeviceRepository;
import com.rengu.operationsoanagementsuite.Utils.NotificationMessage;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class DeviceService {

    public static volatile List<HeartbeatEntity> onlineHeartbeats = new ArrayList<>();
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private DeploymentDesignService deploymentDesignService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Transactional
    public DeviceEntity saveDevices(String projectId, DeviceEntity deviceArgs) {
        if (!projectService.hasProject(projectId)) {
            throw new CustomizeException(NotificationMessage.PROJECT_NOT_FOUND);
        }
        if (StringUtils.isEmpty(deviceArgs.getIp())) {
            throw new CustomizeException(NotificationMessage.DEVICE_IP_NOT_FOUND);
        }
        if (hasIp(projectId, deviceArgs.getIp())) {
            throw new CustomizeException(NotificationMessage.DEVICE_EXISTS);
        }
        DeviceEntity deviceEntity = new DeviceEntity();
        BeanUtils.copyProperties(deviceArgs, deviceEntity, "id", "createTime", "projectEntity");
        deviceEntity.setDeployPath(getDeployPath(deviceArgs));
        deviceEntity.setProjectEntity(projectService.getProjects(projectId));
        return deviceRepository.save(deviceEntity);
    }

    @Transactional
    public void deleteDevices(String deviceId) {
        if (!hasDevices(deviceId)) {
            throw new CustomizeException(NotificationMessage.DEVICE_NOT_FOUND);
        }
        // 从部署设计中移除设备
        deploymentDesignService.deleteDeploymentDesignDetailsByDeviceId(deviceId);
        deviceRepository.delete(deviceId);
    }

    @Transactional
    public DeviceEntity updateDevices(String deviceId, DeviceEntity deviceArgs) {
        if (StringUtils.isEmpty(deviceArgs.getIp())) {
            throw new CustomizeException(NotificationMessage.DEVICE_IP_NOT_FOUND);
        }
        DeviceEntity deviceEntity = getDevices(deviceId);
        if (!deviceArgs.getIp().equals(deviceEntity.getIp())) {
            if (hasIp(deviceEntity.getProjectEntity().getId(), deviceArgs.getIp())) {
                throw new CustomizeException(NotificationMessage.DEVICE_EXISTS);
            }
        }
        BeanUtils.copyProperties(deviceArgs, deviceEntity, "id", "createTime", "projectEntity");
        return deviceRepository.save(deviceEntity);
    }

    @Transactional
    public DeviceEntity getDevices(String deviceId) {
        if (!hasDevices(deviceId)) {
            throw new CustomizeException(NotificationMessage.DEVICE_NOT_FOUND);
        }
        return onlineChecker(progressChecker(deviceRepository.findOne(deviceId)));
    }

    @Transactional
    public List<DeviceEntity> getDevicesByProjectId(String projectId) {
        if (!projectService.hasProject(projectId)) {
            throw new CustomizeException(NotificationMessage.PROJECT_NOT_FOUND);
        }
        return onlineChecker(progressChecker(deviceRepository.findByProjectEntityId(projectId)));
    }

    @Transactional
    public List<DeviceEntity> getDevices() {
        return onlineChecker(progressChecker(deviceRepository.findAll()));
    }

    @Transactional
    public DeviceEntity copyDevices(String deviceId) {
        DeviceEntity deviceArgs = getDevices(deviceId);
        DeviceEntity deviceEntity = new DeviceEntity();
        BeanUtils.copyProperties(deviceArgs, deviceEntity, "id", "createTime", "name", "ip");
        // 设置复制设备的名字
        deviceEntity.setName(deviceArgs.getName() + "-副本");
        // 设置复制组件的IP
        String ip = deviceArgs.getIp();
        while (hasIp(deviceArgs.getProjectEntity().getId(), ip)) {
            String[] strings = ip.split("\\.");
            int temp = Integer.parseInt(strings[3]) + 1;
            ip = strings[0] + "." + strings[1] + "." + strings[2] + "." + temp;
        }
        deviceEntity.setIp(ip);
        deviceEntity.setProjectEntity(deviceArgs.getProjectEntity());
        return deviceRepository.save(deviceEntity);
    }

    // 调整路径分隔符
    public String getDeployPath(DeviceEntity deviceEntity) {
        if (StringUtils.isEmpty(deviceEntity.getDeployPath())) {
            throw new CustomizeException(NotificationMessage.DEVICE_DEPLOY_PATH_NOT_FOUND);
        }
        // 替换斜线方向
        String deployPath = deviceEntity.getDeployPath().replace("\\", "/");
        return deployPath.endsWith("/") ? deployPath : deployPath + "/";
    }

    public DeviceEntity progressChecker(DeviceEntity deviceEntity) {
        if (stringRedisTemplate.hasKey(deviceEntity.getId())) {
            deviceEntity.setProgress(Double.parseDouble(stringRedisTemplate.opsForValue().get(deviceEntity.getId())));
        }
        return deviceEntity;
    }

    public List<DeviceEntity> progressChecker(List<DeviceEntity> deviceEntityList) {
        for (DeviceEntity deviceEntity : deviceEntityList) {
            if (stringRedisTemplate.hasKey(deviceEntity.getId())) {
                deviceEntity.setProgress(Double.parseDouble(stringRedisTemplate.opsForValue().get(deviceEntity.getId())));
            }
        }
        return deviceEntityList;
    }

    public boolean isOnline(String ip) {
        List<HeartbeatEntity> onlineDevices = new ArrayList<>(onlineHeartbeats);
        if (onlineDevices.size() != 0) {
            for (HeartbeatEntity heartbeatEntity : onlineDevices) {
                if (ip.equals(heartbeatEntity.getInetAddress().getHostAddress())) {
                    return true;
                }
            }
        }
        return false;
    }

    public DeviceEntity onlineChecker(DeviceEntity deviceEntity) {
        List<HeartbeatEntity> onlineDevices = new ArrayList<>(onlineHeartbeats);
        if (onlineDevices.size() != 0) {
            for (HeartbeatEntity heartbeatEntity : onlineDevices) {
                if (deviceEntity.getIp().equals(heartbeatEntity.getInetAddress().getHostAddress())) {
                    deviceEntity.setOnline(true);
                    break;
                }
            }
        }
        return deviceEntity;
    }

    public List<DeviceEntity> onlineChecker(List<DeviceEntity> deviceEntityList) {
        List<HeartbeatEntity> onlineDevices = new ArrayList<>(onlineHeartbeats);
        Iterator<HeartbeatEntity> heartbeatEntityIterator = onlineDevices.iterator();
        if (onlineDevices.size() != 0) {
            while (heartbeatEntityIterator.hasNext()) {
                HeartbeatEntity heartbeatEntity = heartbeatEntityIterator.next();
                for (DeviceEntity deviceEntity : deviceEntityList) {
                    if (deviceEntity.getIp().equals(heartbeatEntity.getInetAddress().getHostAddress())) {
                        deviceEntity.setOnline(true);
                        heartbeatEntityIterator.remove();
                        break;
                    }
                }
            }
            // 建立虚拟设备
            for (HeartbeatEntity heartbeatEntity : onlineDevices) {
                DeviceEntity deviceEntity = new DeviceEntity();
                deviceEntity.setName(heartbeatEntity.getInetAddress().getHostName());
                deviceEntity.setIp(heartbeatEntity.getInetAddress().getHostAddress());
                deviceEntity.setVirtual(true);
                deviceEntity.setOnline(true);
                deviceEntityList.add(deviceEntity);
            }
        }
        return deviceEntityList;
    }

    public boolean hasDevices(String deviceId) {
        return deviceRepository.exists(deviceId);
    }

    public boolean hasIp(String projectId, String ip) {
        return deviceRepository.findByProjectEntityIdAndIp(projectId, ip) != null;
    }
}