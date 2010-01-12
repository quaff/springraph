package com.google.code.springraph.test.bean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserDao extends BaseDao {

	@Autowired
	UserManager userManager;

}
