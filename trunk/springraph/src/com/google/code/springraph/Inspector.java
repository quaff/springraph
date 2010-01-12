package com.google.code.springraph;

import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.aop.framework.Advised;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.Ordered;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

public class Inspector implements ApplicationContextAware, InitializingBean,
		ResourceLoaderAware, Ordered {

	private final static String NAME_SEPERATOR = "\\" + "n";

	private ApplicationContext ctx;

	private ResourceLoader resourceLoader;

	private String[] includePatterns;

	private String[] excludePatterns;

	private String target;

	public void setTarget(String target) {
		this.target = target;
	}

	public void setIncludePatterns(String includePatterns) {
		if (StringUtils.hasLength(includePatterns))
			this.includePatterns = includePatterns.split(",");
	}

	public void setExcludePatterns(String excludePatterns) {
		if (StringUtils.hasLength(excludePatterns))
			this.excludePatterns = excludePatterns.split(",");
	}

	public Graph inspect() {

		Graph graph = new Graph("spring");

		Map<String, Object> beans = new HashMap<String, Object>();
		for (String s : ctx.getBeanDefinitionNames()) {
			if (ctx.isSingleton(s) || ctx.isPrototype(s)) {
				Object bean = ctx.getBean(s);

				if (bean instanceof Advised) {
					try {
						Object temp = ((Advised) bean).getTargetSource()
								.getTarget();
						if (temp != null)
							bean = temp;
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				if (bean == this)
					continue;
				if (includePatterns != null && includePatterns.length > 0) {
					boolean matches = false;
					for (String pattern : includePatterns)
						if (matchesWildcard(bean.getClass().getName(), pattern)) {
							matches = true;
							break;
						}
					if (!matches)
						continue;
				} else if (excludePatterns != null
						&& excludePatterns.length > 0) {
					boolean matches = false;
					for (String pattern : excludePatterns)
						if (matchesWildcard(bean.getClass().getName(), pattern)) {
							matches = true;
							break;
						}
					if (matches)
						continue;
				}
				beans.put(fullName(s, bean, ctx.isPrototype(s)), bean);
			}
		}
		for (Map.Entry<String, Object> entry : beans.entrySet()) {
			String name = entry.getKey();
			Object bean = entry.getValue();
			Node node = graph.findNode(name);
			if (node == null) {
				node = new Node(name);
				graph.getNodes().add(node);
			}
			Class<?> clazz = bean.getClass();
			do {
				Field[] fields = clazz.getDeclaredFields();
				for (Field f : fields) {
					try {
						f.setAccessible(true);
						Object value = f.get(bean);
						if (value instanceof Collection) {
							if (f.getName().equals("beans"))
								continue;
							for (Object obj : (Collection) value) {
								for (Map.Entry<String, Object> en : beans
										.entrySet()) {
									if (en.getValue() == obj) {
										addDependency(graph, node, en.getKey());
									}
								}
							}

						} else if (value instanceof Map) {
							if (f.getName().equals("beans"))
								continue;
							for (Map.Entry var : ((Map<?, ?>) value).entrySet()) {
								for (Map.Entry<String, Object> en : beans
										.entrySet()) {
									if (en.getValue() == var.getKey()
											|| en.getValue() == var.getValue()) {
										addDependency(graph, node, en.getKey());
									}
								}
							}
						} else {
							String dependency = null;
							for (Map.Entry<String, Object> en : beans
									.entrySet()) {
								if (en.getValue() == value) {
									dependency = en.getKey();
									break;
								}
							}
							addDependency(graph, node, dependency);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				Method[] methods = clazz.getDeclaredMethods();

				for (Method m : methods) {
					int mod = m.getModifiers();
					Class[] paramTypes = m.getParameterTypes();
					if (m.getReturnType() == Void.TYPE
							&& Modifier.isPublic(mod)
							&& !Modifier.isStatic(mod)
							&& paramTypes.length == 1
							&& m.getName().startsWith("set")) {
						for (Map.Entry<String, Object> en : beans.entrySet()) {
							if (paramTypes[0].isAssignableFrom(en.getValue()
									.getClass())) {
								addDependency(graph, node, en.getKey());
								break;
							}

						}

					}
				}
			} while ((clazz = clazz.getSuperclass()) != null);

		}
		return graph;
	}

	private static String fullName(String beanName, Object bean,
			boolean prototype) {
		StringBuilder sb = new StringBuilder();
		Class clazz = bean.getClass();
		boolean proxy = Proxy.isProxyClass(clazz);
		String className;
		if (proxy) {
			className = clazz.getInterfaces()[0].getName() + NAME_SEPERATOR
					+ "(proxy)";
		} else {
			className = clazz.getName();
		}
		if (beanName.startsWith(className)) {
			sb.append(beanName);
			if (prototype)
				sb.append("(prototype)");
		} else {
			sb.append(beanName);
			sb.append(NAME_SEPERATOR).append(className).toString();
			if (prototype)
				sb.append(NAME_SEPERATOR).append("(prototype)");
		}
		return sb.toString();
	}

	private static boolean matchesWildcard(String text, String pattern) {
		text += '\0';
		pattern += '\0';

		int N = pattern.length();

		boolean[] states = new boolean[N + 1];
		boolean[] old = new boolean[N + 1];
		old[0] = true;

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			states = new boolean[N + 1];
			for (int j = 0; j < N; j++) {
				char p = pattern.charAt(j);

				if (old[j] && (p == '*'))
					old[j + 1] = true;

				if (old[j] && (p == c))
					states[j + 1] = true;
				if (old[j] && (p == '?'))
					states[j + 1] = true;
				if (old[j] && (p == '*'))
					states[j] = true;
				if (old[j] && (p == '*'))
					states[j + 1] = true;
			}
			old = states;
		}
		return states[N];
	}

	private static void addDependency(Graph graph, Node node, String dependency) {
		if (dependency == null)
			return;
		Node n = graph.findNode(dependency);
		if (n == null) {
			n = new Node(dependency);
			graph.getNodes().add(n);
		}
		node.getDependency().add(n);
	}

	public static String render(Graph graph) {
		StringBuilder sb = new StringBuilder();
		sb.append("digraph ").append(graph.getName()).append(" {\n");
		for (Node node : graph.getNodes()) {
			if (node.getDependency().size() == 0) {
				sb.append("\"").append(node.getName()).append("\";\n");
			} else {
				for (Node n : node.getDependency())
					sb.append("\"").append(node.getName()).append("\"").append(
							"->").append("\"").append(n.getName()).append("\"")
							.append(";\n");
			}
		}
		sb.append("}");
		return sb.toString();
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (StringUtils.hasLength(target)) {
			FileWriter writer = new FileWriter(resourceLoader.getResource(
					target).getFile());
			writer.write(render(inspect()));
			writer.close();
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext ctx)
			throws BeansException {
		this.ctx = ctx;

	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;

	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	public static class Graph {

		private String name;

		private Set<Node> nodes = new TreeSet<Node>();

		public Graph(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public Set<Node> getNodes() {
			return nodes;
		}

		public Node findNode(String name) {
			for (Node node : nodes)
				if (node.getName().equals(name))
					return node;
			return null;
		}

	}

	public static class Node implements Comparable<Node> {

		private String name;

		private Set<Node> dependency = new TreeSet<Node>();

		public Node(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public Set<Node> getDependency() {
			return dependency;
		}

		@Override
		public int compareTo(Node o) {
			if (name == null)
				return -1;
			if (o.getName() == null)
				return 1;
			return name.compareTo(o.getName());
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Node other = (Node) obj;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}

		public String toString() {
			return name;
		}

	}

}