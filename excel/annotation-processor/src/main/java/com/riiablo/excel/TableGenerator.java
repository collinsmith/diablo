package com.riiablo.excel;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.Generated;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import org.apache.commons.lang3.time.DateFormatUtils;

import com.riiablo.excel.annotation.PrimaryKey;

public class TableGenerator {
  static final ClassName tableName = ClassName.get(Table.class);

  static final ClassName STRING = ClassName.get("java.lang", "String");

  static String serializerName(ClassName schemaName) {
    return schemaName.simpleName() + tableName.simpleName();
  }

  final ProcessingEnvironment processingEnv;
  final Messager messager;
  final Elements elementUtils;
  final Class generatingClass;
  final String serializerPackage;

  TableGenerator(
      ProcessingEnvironment processingEnv,
      Class generatingClass,
      String serializerPackage
  ) {
    this.processingEnv = processingEnv;
    this.messager = processingEnv.getMessager();
    this.elementUtils = processingEnv.getElementUtils();
    this.generatingClass = generatingClass;
    this.serializerPackage = serializerPackage;
  }

  JavaFile generateFile(SchemaAnnotatedElement schema) {
    String comments = schema.name.canonicalName();
    List<FieldElement> fields = new ArrayList<>(256);
    VariableElement primaryKey = null, firstField = null;
    for (Element element : schema.element.getEnclosedElements()) {
      try {
        PrimaryKeyAnnotatedElement annotatedElement = PrimaryKeyAnnotatedElement.get(element);
        if (annotatedElement != null) {
          if (schema.annotation.indexed()) {
            throw new GenerationException(
                String.format("indexed schema defines a @%s",
                    PrimaryKey.class.getCanonicalName()),
                element)
                .kind(Diagnostic.Kind.WARNING);
          } else if (primaryKey == null) {
            primaryKey = annotatedElement.element;
          } else {
            throw new GenerationException(
                String.format("%s has already been set as the @%s",
                    primaryKey.getSimpleName(),
                    PrimaryKey.class.getCanonicalName()),
                element, annotatedElement.mirror);
          }
        } else if (firstField == null && element.getKind() == ElementKind.FIELD) {
          firstField = (VariableElement) element;
        }
      } catch (GenerationException t) {
        t.printMessage(messager);
      }
    }

    if (primaryKey == null && firstField != null) {
      messager.printMessage(Diagnostic.Kind.WARNING,
          String.format("%s does not declare a @%s, using %s",
              schema.name, PrimaryKey.class.getCanonicalName(), firstField.getSimpleName()),
          schema.element);
      primaryKey = firstField;
    }

    MethodSpec constructor = MethodSpec
        .constructorBuilder()
        .addStatement("super($T.class)", schema.element)
        .build();

    TypeSpec.Builder tableType = newTable(schema, fields)
        .addAnnotation(newGenerated(comments))
        .addMethod(constructor)
        .addMethod(offset(schema))
        .addMethod(primaryKey(primaryKey))
        ;

    return JavaFile
        .builder(serializerPackage, tableType.build())
        .skipJavaLangImports(true)
        .addFileComment(
            "automatically generated by $L, do not modify",
            TableGenerator.class.getCanonicalName())
        .build();
  }

  AnnotationSpec newGenerated(String comments) {
    return AnnotationSpec
        .builder(Generated.class)
        .addMember("value", "$S", generatingClass.getCanonicalName())
        .addMember("date", "$S", DateFormatUtils.ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.format(new Date()))
        .addMember("comments", "$S", comments)
        .build();
  }

  TypeSpec.Builder newTable(SchemaAnnotatedElement schema, List<FieldElement> fields) {
    return TypeSpec
        .classBuilder(serializerName(schema.name))
        .superclass(ParameterizedTypeName.get(tableName, schema.name))
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        ;
  }

  MethodSpec offset(SchemaAnnotatedElement schema) {
    return MethodSpec
        .methodBuilder("offset")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PROTECTED)
        .returns(int.class)
        .addStatement("return $L", schema.annotation.offset())
        .build();
  }

  MethodSpec primaryKey(VariableElement primaryKey) {
    return MethodSpec
        .methodBuilder("primaryKey")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PROTECTED)
        .returns(String.class)
        .addStatement("return $S", primaryKey)
        .build();
  }
}
