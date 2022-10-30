// -*- c-basic-offset: 4; indent-tabs-mode: nil -*-

/*
 * Copyright 2018,2019, Lancaster University
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the
 *    distribution.
 * 
 *  * Neither the name of the copyright holder nor the names of
 *    its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 * Author: Steven Simpson <http://github.com/simpsonst>
 */

package uk.ac.lancs.rest.server.proc;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

import uk.ac.lancs.rest.service.Route;
import uk.ac.lancs.rest.service.Routes;
import uk.ac.lancs.rest.service.Subpath;
import uk.ac.lancs.scc.jardeps.Service;

/**
 * 
 * 
 * @author simpsons
 */
@Service(Processor.class)
public final class RESTStubGenerator extends AbstractProcessor {
    private ProcessingEnvironment procEnv;

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        this.procEnv = processingEnv;
    }

    private ExecutableType getSam(TypeElement samType) {
        return (ExecutableType) samType.getEnclosedElements().stream()
            .filter(ExecutableElement.class::isInstance)
            .map(ExecutableElement.class::cast)
            .filter(e -> e.getModifiers().contains(Modifier.ABSTRACT))
            .reduce((a, b) -> {
                throw new IllegalArgumentException("not SAM type: "
                    + samType);
            }).get().asType();
    }

    private ExecutableType getHandlerType() {
        /* Find the single abstract method of our handler type. */
        TypeElement handlerType = procEnv.getElementUtils()
            .getTypeElement("org.apache.http.protocol.HttpRequestHandler");
        return getSam(handlerType);
    }

    private ExecutableType getExtendedHandlerType() {
        /* Find the single abstract method of our deprecated handler
         * type. */
        TypeElement handlerType = procEnv.getElementUtils()
            .getTypeElement("uk.ac.lancs.rest.server.RESTHandler");
        return getSam(handlerType);
    }

    /**
     * Determine whether the subject type can be invoked as if it were
     * the target.
     * 
     * @param target the type of arguments that we supply, and the
     * return type we expect
     * 
     * @param subject the type being tested
     * 
     * @return {@code true} if the subject is compatible with the target
     */
    private boolean overrides(ExecutableType subject, ExecutableType target) {
        Types types = procEnv.getTypeUtils();
        if (target.getReturnType().getKind() != TypeKind.VOID) {
            if (!types.isAssignable(subject.getReturnType(),
                                    target.getReturnType()))
                return false;
        }
        List<? extends TypeMirror> subParams = subject.getParameterTypes();
        List<? extends TypeMirror> tarParams = target.getParameterTypes();
        if (subParams.size() != tarParams.size()) return false;
        for (int i = 0; i < subParams.size(); i++) {
            if (!types.isAssignable(tarParams.get(i), subParams.get(i)))
                return false;
        }
        return true;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        final ExecutableType handleType = getHandlerType();
        if (handleType == null) {
            procEnv.getMessager().printMessage(Kind.ERROR,
                                               "We're in trouble now.");
            return false;
        }
        final ExecutableType extHandleType = getExtendedHandlerType();
        if (handleType == null) {
            procEnv.getMessager().printMessage(Kind.ERROR,
                                               "We're in trouble now.");
            return false;
        }

        /* Check signature and expression on all appropriately annotated
         * methods. */
        for (Element elem : roundEnv.getElementsAnnotatedWith(Route.class)) {
            ExecutableElement exec = (ExecutableElement) elem;

            /* Check signature. */
            ExecutableType execType = (ExecutableType) exec.asType();
            if (overrides(execType, extHandleType)) {
                procEnv.getMessager()
                    .printMessage(Kind.WARNING,
                                  "Deprecated signature; should be "
                                      + handleType,
                                  exec);
            } else if (!overrides(execType, handleType)) {
                procEnv.getMessager()
                    .printMessage(Kind.ERROR, "Signature not compatible with "
                        + handleType, exec);
            }

            /* Check the regular expression. */
            Route rt = exec.getAnnotation(Route.class);
            Subpath sp = exec.getAnnotation(Subpath.class);
            final String raw = rt.value();
            final String pattern;
            if (sp == null) {
                pattern = Routes.rawAt(raw);
            } else if (sp.required()) {
                pattern = Routes.rawUnder(raw);
            } else {
                pattern = Routes.rawAtOrUnder(raw);
            }
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException ex) {
                TypeElement rtType = procEnv.getElementUtils()
                    .getTypeElement(Route.class.getCanonicalName());
                ExecutableElement valField = rtType.getEnclosedElements()
                    .stream().filter(ExecutableElement.class::isInstance)
                    .map(ExecutableElement.class::cast)
                    .filter(e -> e.getSimpleName().toString().equals("value"))
                    .findAny().get();
                AnnotationMirror iq = exec
                    .getAnnotationMirrors().stream().filter(am -> am
                        .getAnnotationType().asElement().equals(rtType))
                    .findFirst().get();
                AnnotationValue val = iq.getElementValues().get(valField);
                String prefix = ex.getPattern().substring(0, ex.getIndex());
                String suffix = ex.getPattern().substring(ex.getIndex());
                procEnv.getMessager()
                    .printMessage(Kind.ERROR,
                                  ex.getDescription() + " between \u00ab"
                                      + prefix + "\u00bb and \u00ab" + suffix
                                      + "\u00bb",
                                  exec, iq, val);
            }
        }

        /* We claim no annotations. */
        return false;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton("*");
    }
}
