package com.riiablo.excel;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Generated;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

public class SerializerGenerator {
  static final ClassName serializerName = ClassName.get(Serializer.class);

  static final ClassName STRING = ClassName.get("java.lang", "String");
  static final String TMP = "x";

  static String serializerName(ClassName schemaName) {
    return schemaName.simpleName() + serializerName.simpleName();
  }

  final ProcessingEnvironment processingEnv;
  final Messager messager;
  final Elements elementUtils;
  final Class generatingClass;
  final String serializerPackage;

  SerializerGenerator(
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
    for (Element element : schema.element.getEnclosedElements()) {
      switch (element.getKind()) {
        case CONSTRUCTOR: {
          Set<Modifier> modifiers = element.getModifiers();
          if (!modifiers.contains(Modifier.PUBLIC)) {
            throw new GenerationException("no public constructor", element);
          }
          break;
        }
        case FIELD: {
          Set<Modifier> modifiers = element.getModifiers();
          if (!modifiers.contains(Modifier.PUBLIC)) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                "schema fields should be declared public",
                element);
          } else if (modifiers.contains(Modifier.FINAL)) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "schema fields must be mutable",
                element);
          } else {
            try {
              FieldElement field = new FieldElement(elementUtils, element);
              if (field.variableName.contentEquals(TMP)) {
                throw new GenerationException(
                    String.format("\"%s\" is an illegal schema field name", TMP),
                    element);
              }

              fields.add(field);
            } catch (GenerationException t) {
              t.printMessage(messager);
            }
          }
          break;
        }
      }
    }

    TypeSpec.Builder serializerType = newSerializer(schema.name, fields)
        .addAnnotation(newGenerated(comments))
        ;

    return JavaFile
        .builder(serializerPackage, serializerType.build())
        .skipJavaLangImports(true)
        .addFileComment(
            "automatically generated by $L, do not modify",
            SerializerGenerator.class.getCanonicalName())
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

  TypeSpec.Builder newSerializer(ClassName schemaName, List<FieldElement> fields) {
    return TypeSpec
        .classBuilder(serializerName(schemaName))
        .addSuperinterface(ParameterizedTypeName.get(serializerName, schemaName))
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addMethod(readRecord(schemaName, fields))
        .addMethod(writeRecord(schemaName, fields))
        .addMethod(equals(schemaName, fields))
        .addMethod(compare(schemaName, fields))
        ;
  }

  MethodSpec readRecord(ClassName schemaName, List<FieldElement> fields) {
    ParameterSpec entry = ParameterSpec.builder(schemaName, "entry").build();
    ParameterSpec in = ParameterSpec.builder(DataInput.class, "in").build();
    MethodSpec.Builder method = MethodSpec
        .methodBuilder("readRecord")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(entry)
        .addParameter(in)
        ;

    for (FieldElement field : fields) {
      final TypeName type = field.typeName;
      final CodeBlock fieldName = qualify(entry, field.variableName);
      if (field.isArray()) {
        final TypeName componentType = field.componentType;
        final String var = TMP;
        final int length = field.format.endIndex() - field.format.startIndex();
        method.addCode(CodeBlock.builder()
            .addStatement(
                "$L = new $T[$L]", fieldName, componentType, length)
            .beginControlFlow(
                "for (int $1N = $2L; $1N < $3L; $1N++)", var, 0, length)
            .addStatement(
                readX(in, componentType, CodeBlock.of("$L[$N]", fieldName, var)))
            .endControlFlow()
            .build());
      } else {
        method.addCode(CodeBlock.builder()
            .addStatement(readX(in, type, fieldName))
            .build());
      }
    }

    return method.build();
  }

  MethodSpec writeRecord(ClassName schemaName, List<FieldElement> fields) {
    ParameterSpec entry = ParameterSpec.builder(schemaName, "entry").build();
    ParameterSpec out = ParameterSpec.builder(DataOutput.class, "out").build();
    MethodSpec.Builder method = MethodSpec
        .methodBuilder("writeRecord")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(entry)
        .addParameter(out)
        ;

    for (FieldElement field : fields) {
      final TypeName type = field.typeName;
      final CodeBlock fieldName = qualify(entry, field.variableName);
      if (field.isArray()) {
        final TypeName componentType = field.componentType;
        final String var = TMP;
        method.addCode(CodeBlock.builder()
            .beginControlFlow(
                "for ($T $N : $L)", componentType, var, fieldName)
            .addStatement(STRING.equals(componentType)
                ? writeX(out, componentType, defaultString(var))
                : writeX(out, componentType, var))
            .endControlFlow()
            .build());
      } else {
        method.addCode(CodeBlock.builder()
            .addStatement(STRING.equals(type)
                ? writeX(out, type, defaultString(fieldName))
                : writeX(out, type, fieldName))
            .build());
      }
    }

    return method.build();
  }

  MethodSpec equals(ClassName schemaName, List<FieldElement> fields) {
    ParameterSpec e1 = ParameterSpec.builder(schemaName, "e1").build();
    ParameterSpec e2 = ParameterSpec.builder(schemaName, "e2").build();
    MethodSpec.Builder method = MethodSpec
        .methodBuilder("equals")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(boolean.class)
        .addParameter(e1)
        .addParameter(e2)
        ;

    for (FieldElement field : fields) {
      final TypeName type = field.typeName;
      final Name name = field.variableName;
      final CodeBlock e1Field = qualify(e1, name);
      final CodeBlock e2Field = qualify(e2, name);
      final CodeBlock.Builder block = CodeBlock.builder();
      if (type.isPrimitive()) {
        block.beginControlFlow("if ($L != $L)",
            e1Field,
            e2Field);
      } else {
        block.beginControlFlow("if (!$L)",
            equalsX(
                field.isArray() ? Arrays.class : Objects.class,
                e1Field,
                e2Field));
      }

      method.addCode(block
          .addStatement("return false")
          .endControlFlow()
          .build());
    }

    method.addStatement("return true");
    return method.build();
  }

  MethodSpec compare(ClassName schemaName, List<FieldElement> fields) {
    ParameterSpec e1 = ParameterSpec.builder(schemaName, "e1").build();
    ParameterSpec e2 = ParameterSpec.builder(schemaName, "e2").build();
    MethodSpec.Builder method = MethodSpec
        .methodBuilder("compare")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(ParameterizedTypeName.get(Iterable.class, Throwable.class))
        .addParameter(e1)
        .addParameter(e2)
        ;

    TypeName mismatchesType = ParameterizedTypeName.get(ArrayList.class, Throwable.class);
    FieldSpec mismatches = FieldSpec
        .builder(mismatchesType, "mismatches")
        .initializer("new $T()", mismatchesType)
        .build();
    method.addStatement("$T $N = $L", mismatches.type, mismatches, mismatches.initializer);

    for (FieldElement field : fields) {
      final TypeName type = field.typeName;
      final Name name = field.variableName;
      final CodeBlock e1Field = qualify(e1, name);
      final CodeBlock e2Field = qualify(e2, name);
      final CodeBlock.Builder block = CodeBlock.builder();
      if (type.isPrimitive()) {
        block.beginControlFlow("if ($L != $L)",
            e1Field,
            e2Field);
      } else {
        block.beginControlFlow("if (!$L)",
            equalsX(
                field.isArray() ? Arrays.class : Objects.class,
                e1Field,
                e2Field));
      }

      method.addCode(block
          .addStatement(logX(mismatches, format(
              CodeBlock.of("$L does not match: $N=%s, $N=%s", name, e1, e2),
              CodeBlock.of("$L, $L", e1Field, e2Field))))
          .endControlFlow()
          .build());
    }

    method.addStatement("return $N", mismatches);
    return method.build();
  }

  static CodeBlock qualify(Object object, Name field) {
    return CodeBlock.of("$N.$N", object, field);
  }

  static CodeBlock readX(Object in, TypeName type, Object var) {
    return CodeBlock.of("$L = $N.$N$L()", var, in, "read", getIoMethod(type));
  }

  static CodeBlock writeX(Object out, TypeName type, Object var) {
    return CodeBlock.of("$N.$N$L($L)", out, "write", getIoMethod(type), var);
  }

  static CodeBlock equalsX(Type type, Object obj1, Object obj2) {
    return CodeBlock.of("$T.equals($L, $L)", type, obj1, obj2);
  }

  static CodeBlock logX(Object collection, Object message) {
    return CodeBlock.of("$N.$N(new $T($L))", collection, "add", RuntimeException.class, message);
  }

  static CodeBlock format(Object format, Object args) {
    return CodeBlock.of("$T.$N($S, $L)", String.class, "format", format, args);
  }

  static CodeBlock defaultString(Object var) {
    return CodeBlock.of("$T.$N($L)", StringUtils.class, "defaultString", var);
  }

  static String getIoMethod(TypeName type) {
    if (type == TypeName.BYTE) {
      return "8";
    } else if (type == TypeName.SHORT) {
      return "16";
    } else if (type == TypeName.INT) {
      return "32";
    } else if (type == TypeName.LONG) {
      return "64";
    } else if (type == TypeName.BOOLEAN) {
      return "Boolean";
    } else if (STRING.equals(type)) {
      return "String";
    } else {
      throw new UnsupportedOperationException(type + " is not supported!");
    }
  }
}
