package com.google.code.springraph.test.bean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class UserAction {

	@Autowired
	UserManager userManager;
}
