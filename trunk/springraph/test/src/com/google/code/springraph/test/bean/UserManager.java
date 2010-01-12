package com.google.code.springraph.test.bean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserManager {

	@Autowired
	UserDao userDao;

}
