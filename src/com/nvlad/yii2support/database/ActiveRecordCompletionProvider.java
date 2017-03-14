package com.nvlad.yii2support.objectfactory;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.PhpReadWriteAccessDetector;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.NotNull;
import com.intellij.database.view.DatabaseView;

import javax.naming.spi.ObjectFactory;
import java.util.Hashtable;

/**
 * Created by oleg on 16.02.2017.
 */
public class ActiveRecordCompletionProvider extends com.intellij.codeInsight.completion.CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {

        if (! (completionParameters.getPosition().getParent().getParent().getParent() instanceof ArrayCreationExpression) &&
                !(completionParameters.getPosition().getParent().getParent().getParent().getParent() instanceof ArrayCreationExpression)) {
            return;
        }
        ArrayCreationExpression arrayCreation = null;

        if (completionParameters.getPosition().getParent().getParent().getParent() instanceof ArrayCreationExpression) {
            arrayCreation = (ArrayCreationExpression) completionParameters.getPosition().getParent().getParent().getParent();
        } else if (completionParameters.getPosition().getParent().getParent().getParent().getParent() instanceof ArrayCreationExpression &&
                completionParameters.getPosition().getParent().getParent().getParent() instanceof ArrayHashElement &&
                completionParameters.getPosition().getParent().getParent().toString() != "Array value") {
            arrayCreation = (ArrayCreationExpression) completionParameters.getPosition().getParent().getParent().getParent().getParent();
        } else {
            return;
        }

        Hashtable<String, Object> uniqTracker = new Hashtable<>();

        PhpClass phpClass = ObjectFactoryUtil.findClassByArray(arrayCreation);
        if (phpClass == null) {
            // Try to find if an array is a second parameter of \Yii::createObject
            PsiElement parent = arrayCreation.getParent().getParent();
            if (parent != null && parent instanceof MethodReference) {
                MethodReference method = (MethodReference) parent;
                if (method.getName() != null && method.getName().equals("createObject")) {
                    PhpExpression methodClass = method.getClassReference();
                    if (methodClass != null && methodClass.getName() != null && methodClass.getName().equals("Yii")) {
                        PsiElement[] pList = method.getParameters();
                        if (pList.length == 2) { // \Yii::createObject takes 2 paramters
                            phpClass =  ObjectFactoryUtil.getPhpClassUniversal(method.getProject(), (PhpPsiElement) pList[0]);
                        }
                    }
                }
            }
        }
        if (phpClass == null) {
            PhpFile file = (PhpFile)completionParameters.getOriginalFile();
            PsiDirectory dir = file.getContainingDirectory();
            if ( dir != null && dir.getName().equals("config") ) {
                PsiElement parent = arrayCreation.getParent().getParent();
                if (parent instanceof ArrayHashElement) {
                    ArrayHashElement hash = (ArrayHashElement) parent;
                    PsiElement element = hash.getKey();
                    if (element instanceof StringLiteralExpression) {
                        StringLiteralExpression literal = (StringLiteralExpression) element;
                        String key = literal.getContents();
                        phpClass = ObjectFactoryUtil.getStandardPhpClass(PhpIndex.getInstance(literal.getProject()), key);
                    }
                }
            }
        }

        if (phpClass != null) {
            for (Field field : ObjectFactoryUtil.getClassFields(phpClass)) {
                uniqTracker.put(field.getName(), field);
                completionResultSet.addElement(new ObjectFactoryFieldLookupElement((PhpExpression) completionParameters.getPosition().getParent(), field));
            }


            for (Method method : ObjectFactoryUtil.getClassSetMethods(phpClass)) {
                ObjectFactoryMethodLookupElement lookupElem = new ObjectFactoryMethodLookupElement((PhpExpression) completionParameters.getPosition().getParent(), method);
                if (uniqTracker.get(lookupElem.getAsPropertyName()) == null) {
                    completionResultSet.addElement(lookupElem);
                }
            }

        }

    }
}
