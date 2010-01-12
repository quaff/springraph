package com.google.code.springraph.test;

import java.io.IOException;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Main {

	public static void main(String[] args) throws IOException {
		new ClassPathXmlApplicationContext(
				"com/google/code/springraph/test/applicationContext.xml");
	}

}
