package cn.zy.mozhi.app.config;

import cn.zy.mozhi.infrastructure.dao.DraftDao;
import cn.zy.mozhi.infrastructure.dao.MediaRefDao;
import cn.zy.mozhi.infrastructure.dao.UserDao;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "mozhi.mybatis.enabled", havingValue = "true", matchIfMissing = true)
public class MybatisConfiguration {

    @Bean
    public MapperFactoryBean<UserDao> userDao(SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<UserDao> mapperFactoryBean = new MapperFactoryBean<>(UserDao.class);
        mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
        return mapperFactoryBean;
    }

    @Bean
    public MapperFactoryBean<DraftDao> draftDao(SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<DraftDao> mapperFactoryBean = new MapperFactoryBean<>(DraftDao.class);
        mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
        return mapperFactoryBean;
    }

    @Bean
    public MapperFactoryBean<MediaRefDao> mediaRefDao(SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<MediaRefDao> mapperFactoryBean = new MapperFactoryBean<>(MediaRefDao.class);
        mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
        return mapperFactoryBean;
    }
}
