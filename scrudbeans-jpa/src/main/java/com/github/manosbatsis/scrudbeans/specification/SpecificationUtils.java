/**
 *
 * ScrudBeans: Model driven development for Spring Boot
 * -------------------------------------------------------------------
 *
 * Copyright © 2005 Manos Batsis (manosbatsis gmail)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.manosbatsis.scrudbeans.specification;

import com.github.manosbatsis.scrudbeans.api.specification.IPredicateFactory;
import com.github.manosbatsis.scrudbeans.specification.factory.*;
import com.github.manosbatsis.scrudbeans.util.ClassUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * A generic specifications class that builds model predicates
 */
@Slf4j
public class SpecificationUtils<T, PK extends Serializable> {

	private static final HashMap<String, Class> FIELD_TYPE_CACHE = new HashMap<String, Class>();

	private static final HashMap<String, Field> FIELD_CACHE = new HashMap<String, Field>();

	protected static final HashMap<Class, List<Field>> SIMPLE_SEARCH_FIELDs_CACHE = new HashMap<Class, List<Field>>();

	protected static final String SIMPLE_SEARCH_PARAM_NAME = "_all";

	protected static final String SEARCH_MODE = "_searchmode";

	protected static final StringPredicateFactory stringPredicateFactory = new StringPredicateFactory();

	protected static final BooleanPredicateFactory booleanPredicateFactory = new BooleanPredicateFactory();

	protected static final DatePredicateFactory datePredicateFactory = new DatePredicateFactory();

	protected static final LocalDatePredicateFactory localDatePredicateFactory = new LocalDatePredicateFactory();

	protected static final LocalDateTimePredicateFactory localDateTimePredicateFactory = new LocalDateTimePredicateFactory();


	//, , , , , , ,
	protected static final NumberPredicateFactory<Byte> bytePredicateFactory = new NumberPredicateFactory<Byte>(Byte.class);

	protected static final NumberPredicateFactory<Short> shortPredicateFactory = new NumberPredicateFactory<Short>(Short.class);

	protected static final NumberPredicateFactory<Integer> integerPredicateFactory = new NumberPredicateFactory<Integer>(Integer.class);

	protected static final NumberPredicateFactory<Long> longPredicateFactory = new NumberPredicateFactory<Long>(Long.class);

	protected static final NumberPredicateFactory<BigInteger> bigIntegerPredicateFactory = new NumberPredicateFactory<BigInteger>(BigInteger.class);

	protected static final NumberPredicateFactory<Float> floatPredicateFactory = new NumberPredicateFactory<Float>(Float.class);

	protected static final NumberPredicateFactory<Double> doublePredicateFactory = new NumberPredicateFactory<Double>(Double.class);

	protected static final NumberPredicateFactory<BigDecimal> bigDecimalPredicateFactory = new NumberPredicateFactory<BigDecimal>(BigDecimal.class);

	protected static final HashMap<String, IPredicateFactory> factoryForClassMap = new HashMap<String, IPredicateFactory>();

	protected static final String OR = "OR";

	protected static final String AND = "AND";

	protected static List<String> IGNORED_FIELD_NAMES;

	static {
		factoryForClassMap.put(String.class.getCanonicalName(), stringPredicateFactory);
		factoryForClassMap.put(Boolean.class.getCanonicalName(), booleanPredicateFactory);
		factoryForClassMap.put(Date.class.getCanonicalName(), datePredicateFactory);
		factoryForClassMap.put(LocalDate.class.getCanonicalName(), localDatePredicateFactory);
		factoryForClassMap.put(LocalDateTime.class.getCanonicalName(), localDateTimePredicateFactory);

		factoryForClassMap.put(Byte.class.getCanonicalName(), bytePredicateFactory);
		factoryForClassMap.put(Short.class.getCanonicalName(), shortPredicateFactory);
		factoryForClassMap.put(Integer.class.getCanonicalName(), integerPredicateFactory);
		factoryForClassMap.put(Long.class.getCanonicalName(), longPredicateFactory);
		factoryForClassMap.put(BigInteger.class.getCanonicalName(), bigIntegerPredicateFactory);
		factoryForClassMap.put(Float.class.getCanonicalName(), floatPredicateFactory);
		factoryForClassMap.put(Double.class.getCanonicalName(), doublePredicateFactory);
		factoryForClassMap.put(BigDecimal.class.getCanonicalName(), bigDecimalPredicateFactory);

		// init ignore list
		// TODO: pick model-specific excludes from annotation
		String[] ignoredFieldNames = {"page", "direction", "properties", "size", "totalPages", "_searchmode", "_all", "totalElements", "format"};
		IGNORED_FIELD_NAMES = new ArrayList<String>(ignoredFieldNames.length);
		for (int i = 0; i < ignoredFieldNames.length; i++) {
			IGNORED_FIELD_NAMES.add(ignoredFieldNames[i]);
		}
	}

	/**
	 * Register an appropriate predicate factory for the given class
	 *
	 * @param clazz
	 * @param factory
	 */
	public static void addFactoryForClass(Class clazz, IPredicateFactory factory) {
		Assert.notNull(clazz, "clazz cannot be null");
		Assert.notNull(factory, "factory cannot be null");
		log.debug("Registering entity predicate factory {} for entity type {}", factory, clazz);
		factoryForClassMap.put(clazz.getCanonicalName(), factory);
	}

	/**
	 * Get an appropriate predicate factory for the given class
	 * @param clazz
	 * @return
	 */

	public static IPredicateFactory<?> getPredicateFactoryForClass(Class clazz) {
		IPredicateFactory factory = factoryForClassMap.get(clazz.getCanonicalName());

		// lazily add enum factory to cache as needed
		if (factory == null && clazz.isEnum()) {
			factory = new EnumStringPredicateFactory(clazz);
			addFactoryForClass(clazz, factory);
		}
		log.debug("getPredicateFactoryForClass, clazz: {}, factory: {}", clazz, factory);
		return factory;

	}

	/**
	 * Get a (cached) type for the given class' member name
	 *
	 * @param clazz
	 * @param memberPath the member name
	 * @return
	 */
	public static Class getMemberType(Class<?> clazz, String memberPath) {
		Class memberType = null;
		if (!IGNORED_FIELD_NAMES.contains(memberPath)) {

			String key = clazz.getCanonicalName() + "#" + memberPath;
			memberType = FIELD_TYPE_CACHE.get(key);

			// find it if not cached
			if (memberType == null) {
				if (!FIELD_TYPE_CACHE.containsKey(key)) {

					// find target value type
					memberType = ClassUtils.getBeanPropertyType(clazz, memberPath, true);

					if (memberType == null) {
						log.warn("Caching empty result for field {}", key);
						// HashMap handles null values so we can use containsKey to cache
						// invalid fields and skip the search altogether
						FIELD_TYPE_CACHE.put(key, null);
					}
					else {
						FIELD_TYPE_CACHE.put(key, memberType);
						log.debug("getMemberType: added field in cache, key: {}, type: {}", key, memberType);
					}
				}
				else {
					log.warn("getMemberType: found empty field in cache, ignoring key: {}", key);
				}
			}
			else {
				log.debug("getMemberType: found field in cache, key: {}, type: {}", key, memberType);
			}
		}

		return memberType;
	}


	/**
	 * Get a (cached) field for the given class' member name
	 * @param clazz
	 * @param fieldName the member name
	 * @return
	 */
	public static Field getField(Class<?> clazz, String fieldName) {
		Field field = null;
		if (!IGNORED_FIELD_NAMES.contains(fieldName)) {

			String key = clazz.getName() + "#" + fieldName;
			field = FIELD_CACHE.get(key);

			// find it if not cached
			if (field == null && !FIELD_CACHE.containsKey(key)) {
				Class<?> tmpClass = clazz;
				do {
					for (Field tmpField : tmpClass.getDeclaredFields()) {
						String candidateName = tmpField.getName();
						if (candidateName.equals(fieldName)) {
							// field.setAccessible(true);
							FIELD_CACHE.put(key, tmpField);
							field = tmpField;
							break;
						}
					}
					tmpClass = tmpClass.getSuperclass();
				} while (tmpClass != null && field == null);
			}
			if (field == null) {
				log.warn("Field '" + fieldName + "' not found on class " + clazz);
				// HashMap handles null values so we can use containsKey to cach
				// invalid fields and hence skip the reflection scan
				FIELD_CACHE.put(key, null);
			}
			// throw new RuntimeException("Field '" + fieldName +
			// "' not found on class " + clazz);
		}

		return field;
	}

}
