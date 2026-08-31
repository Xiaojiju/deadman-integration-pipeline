package com.mtfm.gateway.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mtfm.gateway.catalog.entity.ProductEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<ProductEntity> {
}
