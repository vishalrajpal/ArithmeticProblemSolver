import csv
import numpy as np
from sklearn.svm import SVC
from sklearn.metrics import confusion_matrix
from numpy import dtype

class SVM_Questions:

    def __init__(self, training_features, testing_features):
        self.m_training_features = training_features
        self.m_testing_features = testing_features
        self.m_label_key = "Label"
        self.m_svc = SVC()

    def train(self):
        n_training_samples = 1015
        n_training_features = 9
        training_data = np.empty((n_training_samples, n_training_features))
        training_labels = np.empty((n_training_samples, 1), dtype=np.int)

        with open(self.m_training_features) as training_csv_file:
            training_features_file = csv.DictReader(training_csv_file)
            for index, ir in enumerate(training_features_file):
                training_labels[index] = np.asarray(ir.pop(self.m_label_key), dtype=np.int)
                training_data[index] = np.asarray(ir.values(), dtype=np.float64)

        self.m_svc.fit(training_data, training_labels)

    def test(self):
        n_test_samples = 202
        self.n_test_samples = n_test_samples
        n_test_features = 9
        test_data = np.empty((n_test_samples, n_test_features))
        test_labels = np.empty((n_test_samples, 1), dtype=np.int)

        with open(self.m_testing_features) as test_csv_file:
            test_features_file = csv.DictReader(test_csv_file)
            for index, ir in enumerate(test_features_file):
                test_labels[index] = np.asarray(ir.pop(self.m_label_key), dtype=np.int)
                test_data[index] = np.asarray(ir.values(), dtype=np.float64)

            self.m_test_labels = test_labels
            self.m_test_data = test_data

    def accuracy(self):
        test_result = []
        for label_ratios in self.m_result:
            max_ratio = -100
            counter = 0
            maxIndex = -1
            for label_ratio in label_ratios:
                counter = counter + 1
                if (label_ratio > max_ratio):
                    max_ratio = label_ratio
                    maxIndex = counter
            test_result.append(maxIndex)

        testcounter = 0
        correct = 0
        for test_label in self.m_test_labels:
            if test_label == test_result[testcounter]:
                correct = correct + 1
            testcounter = testcounter + 1

        print "Test result/"
        print correct
        print float(correct)/self.n_test_samples

    def predict(self):
        predict_result = self.m_svc.predict(self.m_test_data)
        testcounter = 0
        correct = 0
        for test_label in self.m_test_labels:
            if test_label == predict_result[testcounter]:
                correct = correct + 1
            testcounter = testcounter + 1

        print "Predict result"
        print correct
        print float(correct)/self.n_test_samples

        print "Confusion Matrix"
        print confusion_matrix(self.m_test_labels, predict_result)
