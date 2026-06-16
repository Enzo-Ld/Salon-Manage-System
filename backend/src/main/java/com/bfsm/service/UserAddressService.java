package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bfsm.common.BizException;
import com.bfsm.common.SecurityUtils;
import com.bfsm.entity.UserAddress;
import com.bfsm.mapper.UserAddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserAddressService {
    private final UserAddressMapper userAddressMapper;

    public UserAddressService(UserAddressMapper userAddressMapper) {
        this.userAddressMapper = userAddressMapper;
    }

    public List<UserAddress> currentUserAddresses() {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        return userAddressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getUpdatedAt)
                .orderByDesc(UserAddress::getId));
    }

    @Transactional
    public void saveCurrentUserAddress(UserAddress body) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        validate(body);
        body.setUserId(userId);
        if (body.getIsDefault() == null) {
            body.setIsDefault(0);
        }
        if (body.getId() != null) {
            UserAddress exists = currentUserAddress(body.getId());
            body.setUserId(exists.getUserId());
        }
        if (body.getIsDefault() == 1) {
            clearDefault(userId);
        }
        if (body.getId() == null) {
            if (currentUserAddresses().isEmpty()) {
                body.setIsDefault(1);
            }
            userAddressMapper.insert(body);
        } else {
            userAddressMapper.updateById(body);
        }
    }

    @Transactional
    public void deleteCurrentUserAddress(Long id) {
        UserAddress address = currentUserAddress(id);
        userAddressMapper.deleteById(id);
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            List<UserAddress> rest = currentUserAddresses();
            if (!rest.isEmpty()) {
                UserAddress next = rest.get(0);
                next.setIsDefault(1);
                userAddressMapper.updateById(next);
            }
        }
    }

    @Transactional
    public void setDefaultCurrentUserAddress(Long id) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        UserAddress address = currentUserAddress(id);
        clearDefault(userId);
        address.setIsDefault(1);
        userAddressMapper.updateById(address);
    }

    public UserAddress currentUserAddress(Long id) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        UserAddress address = userAddressMapper.selectById(id);
        if (address == null || !userId.equals(address.getUserId())) {
            throw new BizException(404, "收货地址不存在");
        }
        return address;
    }

    private void clearDefault(Long userId) {
        List<UserAddress> addresses = userAddressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1));
        for (UserAddress address : addresses) {
            address.setIsDefault(0);
            userAddressMapper.updateById(address);
        }
    }

    private void validate(UserAddress body) {
        if (!StringUtils.hasText(body.getReceiverName()) || !StringUtils.hasText(body.getReceiverPhone())
                || !StringUtils.hasText(body.getProvince()) || !StringUtils.hasText(body.getCity())
                || !StringUtils.hasText(body.getDistrict()) || !StringUtils.hasText(body.getDetailAddress())) {
            throw new BizException(4401, "请完整填写收货地址信息");
        }
    }
}
