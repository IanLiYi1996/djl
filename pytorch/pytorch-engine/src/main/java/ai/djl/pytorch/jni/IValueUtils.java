/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package ai.djl.pytorch.jni;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.pytorch.engine.PtNDArray;
import ai.djl.pytorch.engine.PtNDManager;
import ai.djl.pytorch.engine.PtSymbolBlock;
import ai.djl.util.Pair;
import ai.djl.util.PairList;
import ai.djl.util.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** IValueUtils is utility class to deal with IValue in PyTorch. */
public final class IValueUtils {

    private IValueUtils() {}

    /**
     * Create IValue Pointer from NDArray.
     *
     * @param arrayHandle the handle for PyTorch Tensor
     * @return IValue Pointer
     */
    public static Pointer toIValuePointer(Pointer arrayHandle) {
        return PyTorchLibrary.LIB.iValueFromTensor(arrayHandle);
    }

    /**
     * Create List IValue Pointer from pointer array.
     *
     * @param pointers pointer array
     * @return IValue Pointer
     */
    public static Pointer iValueFromList(Pointer[] pointers) {
        return PyTorchLibrary.LIB.iValueFromList(pointers);
    }

    /**
     * Create Dict IValue Pointer from pointer with its key name.
     *
     * @param pointers pointer array
     * @param names the key value of the pointer
     * @return IValue Pointer
     */
    public static Pointer iValueFromDict(Pointer[] pointers, String[] names) {
        return PyTorchLibrary.LIB.iValueFromDict(pointers, names);
    }

    /**
     * Check IValue is a container of {@link PtNDArray}.
     *
     * @param iValueHandle IValue {@link Pointer}
     * @return result
     */
    public static boolean isNDArray(Pointer iValueHandle) {
        return PyTorchLibrary.LIB.iValueIsTensor(iValueHandle);
    }

    /**
     * Check IValue is a container of {@link NDList}.
     *
     * @param iValueHandle IValue {@link Pointer}
     * @return result
     */
    public static boolean isNDList(Pointer iValueHandle) {
        return PyTorchLibrary.LIB.iValueIsTensorList(iValueHandle);
    }

    /**
     * Check IValue is a container of IValue List.
     *
     * @param iValueHandle IValue {@link Pointer}
     * @return result
     */
    public static boolean isList(Pointer iValueHandle) {
        return PyTorchLibrary.LIB.iValueIsList(iValueHandle);
    }

    /**
     * Check IValue is a container of IValue Tuple.
     *
     * @param iValueHandle IValue {@link Pointer}
     * @return result
     */
    public static boolean isTuple(Pointer iValueHandle) {
        return PyTorchLibrary.LIB.iValueIsTuple(iValueHandle);
    }

    /**
     * Check IValue is a container of IValue Map.
     *
     * @param iValueHandle IValue {@link Pointer}
     * @return result
     */
    public static boolean isMap(Pointer iValueHandle) {
        return PyTorchLibrary.LIB.iValueIsMap(iValueHandle);
    }

    /**
     * Check IValue is a container of String.
     *
     * @param iValueHandle IValue {@link Pointer}
     * @return result
     */
    public static boolean isString(Pointer iValueHandle) {
        return PyTorchLibrary.LIB.iValueIsString(iValueHandle);
    }

    /**
     * Extract IValue with a {@link PtNDArray} value.
     *
     * @param iValueHandle IValue {@link Pointer}
     * @param manager {@link PtNDManager} that creates {@link PtNDArray}
     * @return {@link ai.djl.ndarray.NDArray}
     */
    public static PtNDArray toNDArray(Pointer iValueHandle, PtNDManager manager) {
        Pointer ndHandle = PyTorchLibrary.LIB.iValueToTensor(iValueHandle);
        return manager.create(ndHandle);
    }

    /**
     * Extract IValue to {@link NDList}.
     *
     * @param iValueHandle IValue {@link Pointer}
     * @param manager {@link PtNDManager} that creates {@link PtNDArray}
     * @return {@link NDList}
     */
    public static NDList toNDList(Pointer iValueHandle, PtNDManager manager) {
        Pointer[] ndHandles = PyTorchLibrary.LIB.iValueToTensorList(iValueHandle);
        NDList list = new NDList();
        for (Pointer handle : ndHandles) {
            list.add(manager.create(handle));
        }
        return list;
    }

    /**
     * Extract IValue to String.
     *
     * @param iValueHandle IValue {@link Pointer}
     * @return String
     */
    public static String toString(Pointer iValueHandle) {
        return PyTorchLibrary.LIB.iValueToString(iValueHandle);
    }

    /**
     * Extract IValue to an IValue Array.
     *
     * @param iValueHandle IValue {@link Pointer}
     * @return IValue array
     */
    public static Pointer[] toIValueArray(Pointer iValueHandle) {
        if (isTuple(iValueHandle)) {
            return PyTorchLibrary.LIB.iValueToListFromTuple(iValueHandle);
        }
        return PyTorchLibrary.LIB.iValueToList(iValueHandle);
    }

    /**
     * Extract IValue to a Map.
     *
     * @param iValueHandle IValue {@link Pointer}
     * @return IValue Map
     */
    public static Map<Pointer, Pointer> toIValueMap(Pointer iValueHandle) {
        Pointer[] iValueHandles = PyTorchLibrary.LIB.iValueToMap(iValueHandle);
        Map<Pointer, Pointer> map = new ConcurrentHashMap<>();
        for (int i = 0; i < iValueHandles.length; i += 2) {
            map.put(iValueHandles[i], iValueHandles[i + 1]);
        }
        return map;
    }

    private static NDList forwardHelper(Pointer iValueHandle, PtNDManager manager) {
        NDList list = new NDList();
        if (isNDArray(iValueHandle)) {
            list.add(toNDArray(iValueHandle, manager));
        } else if (isNDList(iValueHandle)) {
            list.addAll(toNDList(iValueHandle, manager));
        } else if (isList(iValueHandle) || isTuple(iValueHandle)) {
            for (Pointer handle : toIValueArray(iValueHandle)) {
                list.addAll(forwardHelper(handle, manager));
            }
        } else if (isMap(iValueHandle)) {
            // Only allows <String, NDArray> type of map
            Map<Pointer, Pointer> map = toIValueMap(iValueHandle);
            for (Map.Entry<Pointer, Pointer> entry : map.entrySet()) {
                String name = toString(entry.getKey());
                // free the IValue handle
                PyTorchLibrary.LIB.torchDeleteIValue(entry.getKey());
                PtNDArray value = toNDArray(entry.getValue(), manager);
                // free the IValue handle
                PyTorchLibrary.LIB.torchDeleteIValue(entry.getValue());
                value.setName(name);
                list.add(value);
            }
        } else {
            // free the IValue handle
            PyTorchLibrary.LIB.torchDeleteIValue(iValueHandle);
            throw new UnsupportedOperationException("Unsupported IValue type");
        }
        // free the IValue handle
        PyTorchLibrary.LIB.torchDeleteIValue(iValueHandle);
        return list;
    }

    /**
     * Run the forward of PyTorch module.
     *
     * @param block the block that contains PyTorch module
     * @param inputs input {@link NDList}
     * @param isTrain is running on training mode
     * @return result {@link NDList}
     */
    public static NDList forward(PtSymbolBlock block, NDList inputs, boolean isTrain) {
        Pointer[] arrayHandles =
                inputs.stream()
                        .map(input -> ((PtNDArray) input).getHandle())
                        .toArray(Pointer[]::new);
        String[] names = inputs.stream().map(NDArray::getName).toArray(String[]::new);
        Pointer[] iValueInputs = getInputs(arrayHandles, names);
        Pointer result = PyTorchLibrary.LIB.moduleForward(block.getHandle(), iValueInputs, isTrain);
        PtNDManager manager = (PtNDManager) inputs.get(0).getManager();
        return forwardHelper(result, manager);
    }

    private static boolean isNameList(String name) {
        return Pattern.matches("\\w+\\[]", name);
    }

    private static boolean isNameDict(String name) {
        return name.contains(".");
    }

    private static Pointer[] getInputs(Pointer[] arrays, String[] names) {
        List<PairList<String, Pointer>> outputs = new ArrayList<>();
        Map<String, Integer> indexMap = new ConcurrentHashMap<>();
        for (int i = 0; i < arrays.length; i++) {
            String name = names[i];
            if (name == null || (!isNameList(name) && !isNameDict(name))) {
                PairList<String, Pointer> list = new PairList<>();
                list.add(new Pair<>(null, toIValuePointer(arrays[i])));
                outputs.add(list);
                continue;
            }
            String mapKey = null;
            boolean isDict = isNameDict(names[i]);
            if (isDict) {
                String[] strings = names[i].split("\\.");
                Preconditions.checkArgument(
                        strings.length == 2,
                        "Please make sure you only include one '.' in the name. Nested Map is not supported!");
                name = strings[0];
                mapKey = strings[1];
            }
            if (!indexMap.containsKey(name)) {
                outputs.add(new PairList<>());
                indexMap.put(name, outputs.size() - 1);
            }
            if (isDict) {
                outputs.get(indexMap.get(name)).add(new Pair<>(mapKey, arrays[i]));
            } else {
                outputs.get(indexMap.get(name)).add(new Pair<>(name, arrays[i]));
            }
        }
        Pointer[] pointers = new Pointer[outputs.size()];
        for (int i = 0; i < outputs.size(); ++i) {
            // not List, Dict input
            if (outputs.get(i).size() == 1 && outputs.get(i).get(0).getKey() == null) {
                pointers[i] = outputs.get(i).get(0).getValue();
            } else if (isNameList(outputs.get(i).get(0).getKey())) {
                pointers[i] = iValueFromList(outputs.get(i).valueArray(new Pointer[0]));
            } else {
                PairList<String, Pointer> dict = outputs.get(i);
                pointers[i] =
                        iValueFromDict(
                                dict.valueArray(new Pointer[0]), dict.keyArray(new String[0]));
            }
        }
        return pointers;
    }
}
